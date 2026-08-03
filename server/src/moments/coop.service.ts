import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import sharp from 'sharp';
import { COOP_INVITE_TTL_MINUTES } from '../common/constants';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { UploadService } from '../upload/upload.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CreateCoopInviteDto, SubmitCoopMediaDto } from './dto/coop.dto';
import { CoopInvite, CoopInviteView } from './entities/coop-invite.entity';
import { CoopRepository } from './coop.repository';

/** Kich thuoc anh ghep (vuong, chia doi trai/phai). */
const MERGED_SIZE = 1080;

/**
 * Co-op Capture (REDESIGN 2026-08-02 theo yeu cau user):
 * 1. A moi B (KHONG kem anh) — loi moi co hieu luc 5 PHUT.
 * 2. B accept -> ACCEPTED -> ca 2 vao man chup coop (2 client POLL GET :id).
 * 3. Moi nguoi tu chup + nop nua anh cua minh (POST :id/media) — nguoi moi =
 *    nua TRAI, nguoi nhan = nua PHAI.
 * 4. Du 2 nua: server ghep (sharp) + upload Cloudinary -> mergedMediaUrl,
 *    status COMPLETED. Server KHONG tu tao moment nua — moi nguoi tai anh ghep
 *    ve va di dang bai theo luong thuong (edit -> caption -> POST /moments);
 *    streak/quest tinh luc dang bai. Khung COOP_FIRST + friend streak van
 *    duoc cong ngay khi ghep xong.
 */
@Injectable()
export class CoopService {
  private readonly logger = new Logger(CoopService.name);

  constructor(
    private readonly repo: CoopRepository,
    private readonly friendshipsRepo: FriendshipsRepository,
    private readonly friendshipsService: FriendshipsService,
    private readonly usersRepo: UsersRepository,
    private readonly usersService: UsersService,
    private readonly framesService: FramesService,
    private readonly uploadService: UploadService,
  ) {}

  /** Gui loi moi chup chung (chi moi duoc ban be; khong kem anh). */
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
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    });

    await this.pushToUser(
      dto.friendUid,
      'Loi moi chup chung 📸',
      'Ban be vua moi ban chup chung — loi moi co hieu luc 5 phut!',
      { type: 'COOP_INVITE', inviteId: invite.inviteId },
    ).catch((e) => this.logger.warn(`Khong gui duoc FCM loi moi: ${e.message}`));

    return invite;
  }

  /** Danh sach loi moi dang cho minh tra loi (kem ten/avatar nguoi moi; bo loi moi het han). */
  async listPending(uid: string): Promise<CoopInviteView[]> {
    const all = await this.repo.listPendingForInvitee(uid);

    // Loi moi het han: danh dau EXPIRED vao DB (fire-and-forget) de khong con
    // nam trong PENDING mai. Dung TRANSITION transactional de khong ghi de len
    // accept vua xay ra dung moc het han (cung ly do voi getInvite).
    const expired = all.filter((invite) => this.isExpired(invite));
    for (const invite of expired) {
      this.repo
        .transition(invite.inviteId, 'PENDING', 'EXPIRED')
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

  /**
   * Chi tiet loi moi — 2 CLIENT POLL trang thai o man chup coop (chi nguoi moi/
   * nguoi nhan). PENDING qua 5 phut -> danh dau EXPIRED de ben dang doi biet.
   */
  async getInvite(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.assertParticipant(uid, inviteId);
    if (invite.status === 'PENDING' && this.isExpired(invite)) {
      // Danh dau EXPIRED bang TRANSITION transactional, KHONG update tran: accept
      // co the vua chuyen PENDING->ACCEPTED dung moc 5 phut — update tran se ghi
      // de EXPIRED len ACCEPTED, giet oan phien vua accept (fix race 2026-08-03)
      const marked = await this.repo.transition(inviteId, 'PENDING', 'EXPIRED').catch((e) => {
        this.logger.warn(`Khong danh dau EXPIRED duoc: ${(e as Error).message}`);
        return false;
      });
      if (marked) {
        return { ...invite, status: 'EXPIRED' };
      }
      // Thua transition = trang thai vua doi (accept/decline) — tra ve ban that
      return (await this.repo.findById(inviteId)) ?? invite;
    }
    return invite;
  }

  /** Nguoi nhan chap nhan -> ACCEPTED; nguoi moi dang poll se thay va vao man chup. */
  async accept(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.assertPendingInviteOf(uid, inviteId);

    const locked = await this.repo.transition(inviteId, 'PENDING', 'ACCEPTED');
    if (!locked) {
      throw new BadRequestException('Loi moi da duoc xu ly roi.');
    }

    await this.pushToUser(
      invite.inviterId,
      'Loi moi chup chung duoc chap nhan 🎉',
      'Ban be da dong y — vao chup nua anh cua ban ngay!',
      { type: 'COOP_ACCEPTED', inviteId },
    ).catch((e) => this.logger.warn(`Khong gui duoc FCM chap nhan: ${e.message}`));

    return { ...invite, status: 'ACCEPTED' };
  }

  /**
   * Tu choi loi moi PENDING HOAC huy phien coop dang ACCEPTED — CA 2 phia deu
   * huy duoc (2026-08-03: khong co duong huy khi ACCEPTED thi 1 ben thoat man
   * chup la ben kia "doi ban be chup" VO HAN). Transition transactional —
   * khong ghi de duoc len COMPLETED (dang ghep / da ghep xong).
   */
  async decline(uid: string, inviteId: string): Promise<void> {
    const invite = await this.assertParticipant(uid, inviteId);
    if (invite.status !== 'PENDING' && invite.status !== 'ACCEPTED') {
      throw new BadRequestException('Loi moi da duoc xu ly roi.');
    }
    const declined = await this.repo.transition(inviteId, invite.status, 'DECLINED');
    if (!declined) {
      // Trang thai vua doi duoi tay minh (accept / ghep xong / ben kia huy truoc)
      throw new BadRequestException('Loi moi da duoc xu ly roi.');
    }
  }

  /**
   * Nop nua anh cua minh (chi khi ACCEPTED). Nguoi moi = nua TRAI, nguoi nhan =
   * nua PHAI; nop lai truoc khi ghep = ghi de (chup lai). Khi DU 2 nua: khoa
   * ACCEPTED -> COMPLETED bang transaction (2 ben nop cung luc thi chi 1 ben
   * ghep), ghep + upload -> mergedMediaUrl; fail -> tra ve ACCEPTED de thu lai.
   */
  async submitMedia(uid: string, inviteId: string, dto: SubmitCoopMediaDto): Promise<CoopInvite> {
    const invite = await this.assertParticipant(uid, inviteId);
    if (invite.status !== 'ACCEPTED') {
      throw new BadRequestException(
        invite.status === 'PENDING' ? 'Loi moi chua duoc chap nhan.' : 'Loi moi da duoc xu ly roi.',
      );
    }

    const isInviter = invite.inviterId === uid;
    await this.repo.update(
      inviteId,
      isInviter ? { inviterMediaUrl: dto.mediaUrl } : { inviteeMediaUrl: dto.mediaUrl },
    );

    // DOC LAI tu DB: ben kia co the vua nop nua cua ho xong (2 request dan xen)
    const updated = await this.repo.findById(inviteId);
    if (!updated?.inviterMediaUrl || !updated.inviteeMediaUrl) {
      return updated ?? invite; // con thieu 1 nua — client hien "doi ban be chup"
    }

    const locked = await this.repo.transition(inviteId, 'ACCEPTED', 'COMPLETED');
    if (!locked) {
      // Khong gianh duoc quyen ghep: HOAC ben kia dang ghep, HOAC phien vua bi
      // huy (DECLINED) — doc lai trang thai THAT thay vi doan COMPLETED
      // (doan bua thi client huy-phien lai tuong ghep xong — fix 2026-08-03)
      return (await this.repo.findById(inviteId)) ?? { ...updated, status: 'COMPLETED' };
    }

    try {
      const merged = await this.mergeSideBySide(updated.inviterMediaUrl, updated.inviteeMediaUrl);
      const uploaded = await this.uploadService.uploadBuffer(merged, 'snapget/coop');
      await this.repo.update(inviteId, { mergedMediaUrl: uploaded.url });
      updated.mergedMediaUrl = uploaded.url;
      updated.status = 'COMPLETED';
    } catch (e) {
      // Ghep/upload fail -> tra ve ACCEPTED de 1 trong 2 ben nop lai kich hoat ghep
      await this.repo
        .update(inviteId, { status: 'ACCEPTED' })
        .catch(() => this.logger.warn(`Khong revert duoc loi moi ${inviteId} ve ACCEPTED.`));
      throw e;
    }

    // Chup chung = tuong tac qua lai -> friend streak; khung COOP_FIRST mo cho
    // CA 2 (best-effort — anh ghep DA xong, loi hook khong duoc lam client 500)
    await this.friendshipsService
      .registerInteraction(invite.inviterId, invite.inviteeId)
      .catch((e) => this.logger.warn(`Khong cap nhat duoc friend streak: ${(e as Error).message}`));
    await Promise.all([
      this.framesService.unlockCoopFrames(invite.inviterId),
      this.framesService.unlockCoopFrames(invite.inviteeId),
    ]).catch((e) => this.logger.warn(`Khong mo duoc khung chup chung: ${(e as Error).message}`));

    // Bao ben kia (co the chua poll toi) anh ghep da san sang
    const other = isInviter ? invite.inviteeId : invite.inviterId;
    await this.pushToUser(
      other,
      'Chup chung hoan tat 🎉',
      'Anh ghep da san sang — vao chinh sua va dang bai ngay!',
      { type: 'COOP_DONE', inviteId },
    ).catch((e) => this.logger.warn(`Khong gui duoc FCM hoan tat: ${e.message}`));

    return updated;
  }

  /** Loi moi phai ton tai va uid la 1 trong 2 ben (nguoi moi / nguoi nhan). */
  private async assertParticipant(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.repo.findById(inviteId);
    if (!invite) {
      throw new NotFoundException('Khong tim thay loi moi chup chung.');
    }
    if (invite.inviterId !== uid && invite.inviteeId !== uid) {
      throw new ForbiddenException('Loi moi nay khong danh cho ban.');
    }
    return invite;
  }

  /** Accept: chi NGUOI NHAN, loi moi con PENDING va chua het han 5 phut. */
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
      throw new BadRequestException('Loi moi da het han (qua 5 phut).');
    }
    return invite;
  }

  /** Loi moi qua 5 phut chua duoc chap nhan -> het han. */
  private isExpired(invite: CoopInvite): boolean {
    const ageMs = Date.now() - new Date(invite.createdAt).getTime();
    return ageMs > COOP_INVITE_TTL_MINUTES * 60 * 1000;
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
