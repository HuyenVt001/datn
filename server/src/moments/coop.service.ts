import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import sharp from 'sharp';
import {
  COOP_INVITE_TTL_MINUTES,
  COOP_MEDIA_ALLOWED_HOSTS,
  COOP_SESSION_TTL_MINUTES,
} from '../common/constants';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { UploadService } from '../upload/upload.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CreateCoopInviteDto, SubmitCoopMediaDto } from './dto/coop.dto';
import { CoopInvite, CoopInviteView } from './entities/coop-invite.entity';
import { CoopRepository } from './coop.repository';

/** Kich thuoc anh ghep (vuong, chia doi trai/phai). */
const MERGED_SIZE = 1080;

/** data.type cua push data-only bao nguoi nhan: loi moi vua bi nguoi moi huy. */
const PUSH_COOP_CANCELLED = 'COOP_CANCELLED';

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
 * 5. (2026-11-16) Phien ACCEPTED co han 10 phut (COOP_SESSION_TTL_MINUTES) — qua
 *    han chua ghep xong -> EXPIRED. Moi response kem expiresAt + remainingSeconds
 *    (tinh luc tra ve) de app dem nguoc. Gui lai loi moi cho CUNG 1 nguoi khi
 *    loi moi cu con PENDING -> tra ve loi moi cu (khong spam push).
 */
@Injectable()
export class CoopService {
  private readonly logger = new Logger(CoopService.name);

  constructor(
    private readonly repo: CoopRepository,
    private readonly friendshipsRepo: FriendshipsRepository,
    private readonly usersRepo: UsersRepository,
    private readonly usersService: UsersService,
    private readonly framesService: FramesService,
    private readonly uploadService: UploadService,
  ) {}

  /** Gui loi moi chup chung (chi moi duoc ban be; khong kem anh). */
  async createInvite(inviterId: string, dto: CreateCoopInviteDto): Promise<CoopInvite> {
    if (inviterId === dto.friendUid) {
      throw new BadRequestException("You can't invite yourself to a co-op capture.");
    }
    const pair = await this.friendshipsRepo.findPair(inviterId, dto.friendUid);
    if (!pair || pair.status !== 'ACCEPTED') {
      throw new ForbiddenException('You can only do co-op captures with friends.');
    }

    // Chong gui lap (2026-11-16): da co loi moi CON HAN cho dung nguoi nay -> tra
    // ve loi moi do (idempotent, khong push lan nua). Loi moi cu da qua han -> danh
    // dau EXPIRED roi tao moi binh thuong.
    const existing = await this.repo.listPendingBetween(inviterId, dto.friendUid);
    for (const old of existing.filter((invite) => this.isExpired(invite))) {
      await this.repo
        .transition(old.inviteId, 'PENDING', 'EXPIRED')
        .catch((e) => this.logger.warn(`Failed to mark EXPIRED: ${(e as Error).message}`));
    }
    const stillValid = existing.find((invite) => !this.isExpired(invite));
    if (stillValid) {
      return this.present(stillValid);
    }

    const invite = await this.repo.create({
      inviterId,
      inviteeId: dto.friendUid,
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    });

    // Push kem TEN nguoi moi (truoc chi "A friend" — nguoi nhan khong biet ai moi)
    const inviter = await this.usersRepo.findByUid(inviterId).catch(() => null);
    const inviterName = inviter?.fullName || 'A friend';
    await this.pushToUser(
      dto.friendUid,
      `${inviterName} wants to snap together 📸`,
      `Tap to join the co-op capture — the invite expires in ${COOP_INVITE_TTL_MINUTES} minutes!`,
      { type: 'COOP_INVITE', inviteId: invite.inviteId },
    ).catch((e) => this.logger.warn(`Failed to send invite FCM: ${e.message}`));

    return this.present(invite);
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
        .catch((e) => this.logger.warn(`Failed to mark EXPIRED: ${(e as Error).message}`));
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
        ...this.present(invite),
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
        this.logger.warn(`Failed to mark EXPIRED: ${(e as Error).message}`);
        return false;
      });
      if (marked) {
        return { ...invite, status: 'EXPIRED' };
      }
      // Thua transition = trang thai vua doi (accept/decline) — tra ve ban that
      return this.present((await this.repo.findById(inviteId)) ?? invite);
    }
    if (invite.status === 'ACCEPTED' && this.isSessionExpired(invite)) {
      // Phien chup qua 10 phut chua ghep xong (vd 2 ben bi kill app) -> EXPIRED
      const marked = await this.repo.transition(inviteId, 'ACCEPTED', 'EXPIRED').catch(() => false);
      if (marked) {
        return { ...invite, status: 'EXPIRED' };
      }
      return this.present((await this.repo.findById(inviteId)) ?? invite);
    }
    return this.present(invite);
  }

  /** Nguoi nhan chap nhan -> ACCEPTED; nguoi moi dang poll se thay va vao man chup. */
  async accept(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.assertPendingInviteOf(uid, inviteId);

    const locked = await this.repo.transition(inviteId, 'PENDING', 'ACCEPTED');
    if (!locked) {
      throw new BadRequestException('This invite has already been handled.');
    }

    await this.pushToUser(
      invite.inviterId,
      'Co-op invite accepted 🎉',
      'Your friend is in! Jump in and snap your half now.',
      { type: 'COOP_ACCEPTED', inviteId },
    ).catch((e) => this.logger.warn(`Failed to send accept FCM: ${e.message}`));

    // Han phien 10 phut tinh tu luc accept (transition vua ghi respondedAt ~ bay gio)
    return this.present({ ...invite, status: 'ACCEPTED', respondedAt: new Date().toISOString() });
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
      throw new BadRequestException('This invite has already been handled.');
    }
    const declined = await this.repo.transition(inviteId, invite.status, 'DECLINED');
    if (!declined) {
      // Trang thai vua doi duoi tay minh (accept / ghep xong / ben kia huy truoc)
      throw new BadRequestException('This invite has already been handled.');
    }

    // NGUOI MOI huy loi moi con PENDING -> bao nguoi nhan (data-only, khong hien
    // thong bao) de app go the loi moi ngay, khong doi lan poll sau (2026-11-16).
    // Cac truong hop khac ben kia dang o man chup va tu poll thay DECLINED.
    if (invite.status === 'PENDING' && invite.inviterId === uid) {
      await this.usersService
        .pushDataToUids([invite.inviteeId], { type: PUSH_COOP_CANCELLED, inviteId })
        .catch((e) => this.logger.warn(`Failed to send cancel FCM: ${(e as Error).message}`));
    }
  }

  /**
   * Nop nua anh cua minh (chi khi ACCEPTED). Nguoi moi = nua TRAI, nguoi nhan =
   * nua PHAI; nop lai truoc khi ghep = ghi de (chup lai). Khi DU 2 nua: khoa
   * ACCEPTED -> COMPLETED bang transaction (2 ben nop cung luc thi chi 1 ben
   * ghep), ghep + upload -> mergedMediaUrl; fail -> tra ve ACCEPTED de thu lai.
   */
  async submitMedia(uid: string, inviteId: string, dto: SubmitCoopMediaDto): Promise<CoopInvite> {
    this.assertAllowedMediaUrl(dto.mediaUrl);
    const invite = await this.assertParticipant(uid, inviteId);
    if (invite.status !== 'ACCEPTED') {
      throw new BadRequestException(
        invite.status === 'PENDING'
          ? "This invite hasn't been accepted yet."
          : 'This invite has already been handled.',
      );
    }
    if (this.isSessionExpired(invite)) {
      await this.repo.transition(inviteId, 'ACCEPTED', 'EXPIRED').catch(() => false);
      throw new BadRequestException('The co-op capture session has expired.');
    }

    const isInviter = invite.inviterId === uid;
    await this.repo.update(
      inviteId,
      isInviter ? { inviterMediaUrl: dto.mediaUrl } : { inviteeMediaUrl: dto.mediaUrl },
    );

    // DOC LAI tu DB: ben kia co the vua nop nua cua ho xong (2 request dan xen)
    const updated = await this.repo.findById(inviteId);
    if (!updated?.inviterMediaUrl || !updated.inviteeMediaUrl) {
      return this.present(updated ?? invite); // con thieu 1 nua — client hien "doi ban be chup"
    }

    const locked = await this.repo.transition(inviteId, 'ACCEPTED', 'COMPLETED');
    if (!locked) {
      // Khong gianh duoc quyen ghep: HOAC ben kia dang ghep, HOAC phien vua bi
      // huy (DECLINED) — doc lai trang thai THAT thay vi doan COMPLETED
      // (doan bua thi client huy-phien lai tuong ghep xong — fix 2026-08-03)
      return this.present(
        (await this.repo.findById(inviteId)) ?? { ...updated, status: 'COMPLETED' },
      );
    }

    try {
      const merged = await this.mergeSideBySide(updated.inviterMediaUrl, updated.inviteeMediaUrl);
      const uploaded = await this.uploadService.uploadBuffer(merged, 'snapget/coop');
      await this.repo.update(inviteId, { mergedMediaUrl: uploaded.url });
      updated.mergedMediaUrl = uploaded.url;
      updated.status = 'COMPLETED';
    } catch (e) {
      // Ghep/upload fail -> tra ve ACCEPTED de 1 trong 2 ben nop lai kich hoat ghep.
      // Dung TRANSITION co khoa (2026-11-16, truoc la update tran) — dong bo voi moi
      // lan doi trang thai khac, khong bao gio ghi de len trang thai la.
      await this.repo
        .transition(inviteId, 'COMPLETED', 'ACCEPTED')
        .catch(() => this.logger.warn(`Failed to revert invite ${inviteId} to ACCEPTED.`));
      throw e;
    }

    // Khung COOP_FIRST mo cho CA 2 (best-effort — anh ghep DA xong, loi hook khong
    // duoc lam client 500). Friend streak da bo 2026-11-15.
    await Promise.all([
      this.framesService.unlockCoopFrames(invite.inviterId),
      this.framesService.unlockCoopFrames(invite.inviteeId),
    ]).catch((e) => this.logger.warn(`Failed to unlock co-op frame: ${(e as Error).message}`));

    // Bao ben kia (co the chua poll toi) anh ghep da san sang
    const other = isInviter ? invite.inviteeId : invite.inviterId;
    await this.pushToUser(
      other,
      'Co-op capture complete 🎉',
      'Your combined photo is ready. Edit and post it now!',
      { type: 'COOP_DONE', inviteId },
    ).catch((e) => this.logger.warn(`Failed to send completion FCM: ${e.message}`));

    return updated;
  }

  /** Loi moi phai ton tai va uid la 1 trong 2 ben (nguoi moi / nguoi nhan). */
  private async assertParticipant(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.repo.findById(inviteId);
    if (!invite) {
      throw new NotFoundException('Co-op invite not found.');
    }
    if (invite.inviterId !== uid && invite.inviteeId !== uid) {
      throw new ForbiddenException("This invite isn't for you.");
    }
    return invite;
  }

  /** Accept: chi NGUOI NHAN, loi moi con PENDING va chua het han 5 phut. */
  private async assertPendingInviteOf(uid: string, inviteId: string): Promise<CoopInvite> {
    const invite = await this.repo.findById(inviteId);
    if (!invite) {
      throw new NotFoundException('Co-op invite not found.');
    }
    if (invite.inviteeId !== uid) {
      throw new ForbiddenException("This invite isn't for you.");
    }
    if (invite.status !== 'PENDING') {
      throw new BadRequestException('This invite has already been handled.');
    }
    if (this.isExpired(invite)) {
      throw new BadRequestException('This invite has expired (over 5 minutes).');
    }
    return invite;
  }

  /** Loi moi qua 5 phut chua duoc chap nhan -> het han. */
  private isExpired(invite: CoopInvite): boolean {
    const ageMs = Date.now() - new Date(invite.createdAt).getTime();
    return ageMs > COOP_INVITE_TTL_MINUTES * 60 * 1000;
  }

  /** Moc het han phien ACCEPTED (10 phut tu luc accept); thieu respondedAt -> tinh tu createdAt. */
  private sessionDeadline(invite: CoopInvite): number {
    const start = new Date(invite.respondedAt || invite.createdAt).getTime();
    return start + COOP_SESSION_TTL_MINUTES * 60 * 1000;
  }

  private isSessionExpired(invite: CoopInvite): boolean {
    return Date.now() > this.sessionDeadline(invite);
  }

  /**
   * Gan expiresAt + remainingSeconds (khong luu DB) cho trang thai con dem gio:
   * PENDING = han loi moi, ACCEPTED = han phien chup. Trang thai khac giu nguyen.
   */
  private present<T extends CoopInvite>(invite: T): T {
    let deadline: number | null = null;
    if (invite.status === 'PENDING') {
      deadline = new Date(invite.createdAt).getTime() + COOP_INVITE_TTL_MINUTES * 60 * 1000;
    } else if (invite.status === 'ACCEPTED') {
      deadline = this.sessionDeadline(invite);
    }
    if (deadline === null || Number.isNaN(deadline)) {
      return invite;
    }
    return {
      ...invite,
      expiresAt: new Date(deadline).toISOString(),
      remainingSeconds: Math.max(0, Math.floor((deadline - Date.now()) / 1000)),
    };
  }

  /**
   * Chong SSRF (SECURITY.md P13, 2026-11-16): server se TAI anh tu URL nay de ghep
   * -> chi nhan https tu host Cloudinary (URL ma POST /upload tra ve), khong cho
   * tro vao mang noi bo / metadata endpoint.
   */
  private assertAllowedMediaUrl(mediaUrl: string): void {
    let parsed: URL;
    try {
      parsed = new URL(mediaUrl);
    } catch {
      throw new BadRequestException('Invalid image URL.');
    }
    if (parsed.protocol !== 'https:' || !COOP_MEDIA_ALLOWED_HOSTS.includes(parsed.hostname)) {
      throw new BadRequestException('The image URL must be a photo uploaded through Snapget.');
    }
  }

  /** Ghep split-screen: moi nua crop 'cover' ve 540x1080, dat canh nhau thanh anh vuong 1080. */
  private async mergeSideBySide(leftUrl: string, rightUrl: string): Promise<Buffer> {
    const half = MERGED_SIZE / 2;
    const [leftBuf, rightBuf] = await Promise.all([
      this.downloadImage(leftUrl),
      this.downloadImage(rightUrl),
    ]);
    // .rotate() KHONG doi so = tu xoay theo EXIF orientation roi go tag: anh CameraX
    // (camera sau) luu pixel NGANG + co EXIF "xoay 90°" — sharp mac dinh bo qua EXIF
    // nen thieu buoc nay la anh ghep bi xoay 90° so voi luc chup (fix 2026-08-03)
    const [left, right] = await Promise.all([
      sharp(leftBuf).rotate().resize(half, MERGED_SIZE, { fit: 'cover' }).toBuffer(),
      sharp(rightBuf).rotate().resize(half, MERGED_SIZE, { fit: 'cover' }).toBuffer(),
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
      throw new BadRequestException("Couldn't download the photos to merge.");
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
