import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import sharp from 'sharp';
import { COOP_INVITE_TTL_HOURS } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { QuestsService } from '../quests/quests.service';
import { UploadService } from '../upload/upload.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { AcceptCoopInviteDto, CreateCoopInviteDto } from './dto/coop.dto';
import { CoopInvite, CoopInviteView } from './entities/coop-invite.entity';
import { Moment } from './entities/moment.entity';
import { CoopRepository } from './coop.repository';
import { MomentsService } from './moments.service';

/** Kich thuoc anh ghep (vuong, chia doi trai/phai). */
const MERGED_SIZE = 1080;

/**
 * Co-op Capture (chup chung): A chup nua anh + gui loi moi -> B chap nhan +
 * chup nua con lai -> server GHEP 2 anh (sharp, split trai/phai) thanh 1 moment
 * dang boi A kem coopUserId = B (hien tren feed cua ca 2 vi la ban be).
 */
@Injectable()
export class CoopService {
  private readonly logger = new Logger(CoopService.name);

  constructor(
    private readonly repo: CoopRepository,
    private readonly momentsService: MomentsService,
    private readonly friendshipsRepo: FriendshipsRepository,
    private readonly friendshipsService: FriendshipsService,
    private readonly usersRepo: UsersRepository,
    private readonly usersService: UsersService,
    private readonly questsService: QuestsService,
    private readonly framesService: FramesService,
    private readonly uploadService: UploadService,
  ) {}

  /** Gui loi moi chup chung (chi moi duoc ban be). */
  async createInvite(inviterId: string, dto: CreateCoopInviteDto): Promise<CoopInvite> {
    if (inviterId === dto.friendUid) {
      throw new BadRequestException('Khong the moi chinh minh chup chung.');
    }
    const pair = await this.friendshipsRepo.findPair(inviterId, dto.friendUid);
    if (!pair || pair.status !== 'ACCEPTED') {
      throw new ForbiddenException('Chi moi chup chung duoc voi ban be.');
    }

    const invite = await this.repo.create({
      inviterId,
      inviteeId: dto.friendUid,
      inviterMediaUrl: dto.mediaUrl,
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    });

    await this.pushToUser(
      dto.friendUid,
      'Loi moi chup chung 📸',
      'Ban be vua moi ban chup nua con lai cua khoanh khac!',
      { type: 'COOP_INVITE', inviteId: invite.inviteId },
    ).catch((e) => this.logger.warn(`Khong gui duoc FCM loi moi: ${e.message}`));

    return invite;
  }

  /** Danh sach loi moi dang cho minh tra loi (kem ten/avatar nguoi moi; bo loi moi het han). */
  async listPending(uid: string): Promise<CoopInviteView[]> {
    const all = await this.repo.listPendingForInvitee(uid);

    // Loi moi het han: danh dau EXPIRED vao DB (fire-and-forget) de khong con
    // nam trong PENDING mai — tranh danh sach phinh vo han theo thoi gian.
    const expired = all.filter((invite) => this.isExpired(invite));
    for (const invite of expired) {
      this.repo
        .update(invite.inviteId, { status: 'EXPIRED' })
        .catch((e) => this.logger.warn(`Khong danh dau EXPIRED duoc: ${(e as Error).message}`));
    }

    const invites = all.filter((invite) => !this.isExpired(invite));

    // Gom uid nguoi moi (khu trung) roi fetch 1 lan — tranh N+1 khi 1 ban gui nhieu loi moi
    const inviterIds = [...new Set(invites.map((invite) => invite.inviterId))];
    const inviters = new Map(
      await Promise.all(
        inviterIds.map(async (id) => [id, await this.usersRepo.findByUid(id)] as const),
      ),
    );

    return invites.map((invite) => {
      const inviter = inviters.get(invite.inviterId);
      return {
        ...invite,
        inviterName: inviter?.fullName ?? 'Snapget user',
        inviterAvatar: inviter?.avatar,
      };
    });
  }

  /** Chap nhan: nop nua anh con lai -> ghep -> upload -> tao moment chung. */
  async accept(uid: string, inviteId: string, dto: AcceptCoopInviteDto): Promise<Moment> {
    const invite = await this.assertPendingInviteOf(uid, inviteId);

    // KHOA loi moi TRUOC khi ghep (transaction PENDING->COMPLETED): buoc ghep anh
    // mat vai giay, khong khoa thi 2 request accept dong thoi se tao 2 moment.
    const locked = await this.repo.markCompletedIfPending(inviteId);
    if (!locked) {
      throw new BadRequestException('Loi moi da duoc xu ly roi.');
    }

    let moment: Moment;
    try {
      // Ghep 2 nua anh: trai = nguoi moi, phai = nguoi nhan
      const merged = await this.mergeSideBySide(invite.inviterMediaUrl, dto.mediaUrl);
      const uploaded = await this.uploadService.uploadBuffer(merged, 'snapget/coop');

      // Moment dang boi NGUOI MOI (streak/quest/FCM ban be cua ho chay trong create)
      moment = await this.momentsService.create(
        { uid: invite.inviterId } as AuthUser,
        { contentType: 'PHOTO', mediaUrl: uploaded.url, caption: dto.caption },
        invite.inviteeId,
      );
    } catch (e) {
      // Ghep/upload/tao moment fail -> tra loi moi ve PENDING de thu lai duoc
      await this.repo
        .update(inviteId, { status: 'PENDING' })
        .catch(() => this.logger.warn(`Khong revert duoc loi moi ${inviteId} ve PENDING.`));
      throw e;
    }

    // Best-effort: moment DA dang thanh cong — loi ghi momentId vao invite khong
    // duoc phep lam client nhan 500 + bo qua streak/quest/FCM phia sau.
    await this.repo
      .update(inviteId, { momentId: moment.momentId })
      .catch((e) =>
        this.logger.warn(
          `Khong ghi duoc momentId vao loi moi ${inviteId}: ${(e as Error).message}`,
        ),
      );

    // Nguoi nhan cung duoc tinh hoat dong: streak + quest + friend streak voi nguoi moi
    try {
      const streak = await this.usersService.registerActivityForStreak(uid);
      await this.questsService.registerMomentPosted(uid, streak);
      await this.friendshipsService.registerInteraction(uid, invite.inviterId);
    } catch (e) {
      this.logger.warn(`Khong cap nhat duoc streak/quest cho nguoi nhan: ${(e as Error).message}`);
    }

    // Khung dieu kien COOP_FIRST: hoan thanh chup chung -> mo cho CA 2 nguoi (best-effort).
    await Promise.all([
      this.framesService.unlockCoopFrames(invite.inviterId),
      this.framesService.unlockCoopFrames(invite.inviteeId),
    ]).catch((e) => this.logger.warn(`Khong mo duoc khung chup chung: ${(e as Error).message}`));

    await this.pushToUser(
      invite.inviterId,
      'Chup chung hoan tat 🎉',
      'Ban be da chup nua con lai — khoanh khac chung da len feed!',
      { type: 'COOP_DONE', momentId: moment.momentId },
    ).catch((e) => this.logger.warn(`Khong gui duoc FCM hoan tat: ${e.message}`));

    return moment;
  }

  /** Tu choi loi moi (transition transactional — khong ghi de duoc len COMPLETED). */
  async decline(uid: string, inviteId: string): Promise<void> {
    await this.assertPendingInviteOf(uid, inviteId);
    const declined = await this.repo.markDeclinedIfPending(inviteId);
    if (!declined) {
      // Accept vua khoa loi moi trong luc minh bam tu choi
      throw new BadRequestException('Loi moi da duoc xu ly roi.');
    }
  }

  private async assertPendingInviteOf(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.repo.findById(inviteId);
    if (!invite) {
      throw new NotFoundException('Khong tim thay loi moi chup chung.');
    }
    if (invite.inviteeId !== uid) {
      throw new ForbiddenException('Loi moi nay khong danh cho ban.');
    }
    if (invite.status !== 'PENDING') {
      throw new BadRequestException('Loi moi da duoc xu ly roi.');
    }
    if (this.isExpired(invite)) {
      throw new BadRequestException('Loi moi da het han (qua 24 gio).');
    }
    return invite;
  }

  /** Loi moi qua 24h chua tra loi -> het han. */
  private isExpired(invite: CoopInvite): boolean {
    const ageMs = Date.now() - new Date(invite.createdAt).getTime();
    return ageMs > COOP_INVITE_TTL_HOURS * 60 * 60 * 1000;
  }

  /** Ghep split-screen: moi nua crop 'cover' ve 540x1080, dat canh nhau thanh anh vuong 1080. */
  private async mergeSideBySide(leftUrl: string, rightUrl: string): Promise<Buffer> {
    const half = MERGED_SIZE / 2;
    const [leftBuf, rightBuf] = await Promise.all([
      this.downloadImage(leftUrl),
      this.downloadImage(rightUrl),
    ]);
    const [left, right] = await Promise.all([
      sharp(leftBuf).resize(half, MERGED_SIZE, { fit: 'cover' }).toBuffer(),
      sharp(rightBuf).resize(half, MERGED_SIZE, { fit: 'cover' }).toBuffer(),
    ]);
    return sharp({
      create: {
        width: MERGED_SIZE,
        height: MERGED_SIZE,
        channels: 3,
        background: { r: 0, g: 0, b: 0 },
      },
    })
      .composite([
        { input: left, left: 0, top: 0 },
        { input: right, left: half, top: 0 },
      ])
      .jpeg({ quality: 92 })
      .toBuffer();
  }

  private async downloadImage(url: string): Promise<Buffer> {
    const res = await fetch(url);
    if (!res.ok) {
      throw new BadRequestException('Khong tai duoc anh de ghep.');
    }
    return Buffer.from(await res.arrayBuffer());
  }

  /** Gui FCM cho 1 user (helper push chung o UsersService, data values phai la string). */
  private async pushToUser(
    uid: string,
    title: string,
    body: string,
    data: Record<string, string>,
  ): Promise<void> {
    await this.usersService.pushToUids([uid], title, body, data);
  }
}
