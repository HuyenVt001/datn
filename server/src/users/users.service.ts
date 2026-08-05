import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { randomBytes } from 'crypto';
import { AstriteService } from '../astrite/astrite.service';
import { dateKey, INVITE_LINK_TTL_DAYS } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FirebaseService } from '../firebase/firebase.service';
import { UpdateUserDto } from './dto/update-user.dto';
import { emptyPity, PublicUser, User } from './entities/user.entity';
import { UsersRepository } from './users.repository';

@Injectable()
export class UsersService {
  private readonly logger = new Logger(UsersService.name);

  constructor(
    private readonly usersRepo: UsersRepository,
    private readonly firebase: FirebaseService,
    private readonly astrite: AstriteService,
  ) {}

  /**
   * Dam bao user doc ton tai va DAY DU (goi khi dang nhap lan dau).
   * - Doc day du (da co joinDate) -> tra ve luon.
   * - Chua co doc, hoac chi co doc STUB (tao boi grant khung / arrayUnion truoc
   *   khi user dang nhap lan dau) / doc thoi prototype thieu field -> backfill
   *   phan thieu bang set-merge, KHONG ghi de gia tri nguoi dung da co.
   * Fix 2026-07-26: khong dua key co gia tri undefined vao Firestore (vd token
   * email/password khong co claim `picture`) — firebase-admin se throw va
   * GET /users/me 500 vinh vien voi tai khoan dang ky bang email.
   */
  async ensureUser(authUser: AuthUser): Promise<User> {
    const existing = await this.usersRepo.findByUid(authUser.uid);
    if (existing?.joinDate) {
      return existing;
    }

    const patch: Partial<User> = {
      email: existing?.email || authUser.email || '',
      fullName:
        existing?.fullName ||
        (authUser.name as string) ||
        authUser.email?.split('@')[0] ||
        'Snapget user',
      joinDate: new Date().toISOString(),
      personalStreak: existing?.personalStreak ?? 0,
    };
    const avatar = existing?.avatar || (authUser.picture as string | undefined);
    if (avatar) {
      patch.avatar = avatar;
    }
    if (!existing) {
      // Doc stub da co unlockedFrames/fcmTokens -> khong dinh vao de khoi ghi de
      patch.unlockedFrames = [];
      patch.fcmTokens = [];
      patch.unlockedSkins = [];
      patch.unlockedEffects = [];
      patch.gachaPity = emptyPity();
    }
    await this.usersRepo.update(authUser.uid, patch);

    // Thuong tan thu 1600 Astrite — chay SAU khi doc user da ton tai, va tu no
    // idempotent qua co `signupBonusClaimed` nen goi lai nhieu lan van an toan.
    // KHONG gop vao `patch` o tren: moi thay doi so du phai di qua AstriteService
    // de con ghi so cai (astriteTransactions).
    await this.astrite.grantSignupBonusOnce(authUser.uid);

    const base: User = existing ?? {
      uid: authUser.uid,
      email: '',
      fullName: '',
      joinDate: '',
      personalStreak: 0,
      unlockedFrames: [],
      fcmTokens: [],
      astrite: 0,
      unlockedSkins: [],
      unlockedEffects: [],
      gachaPity: emptyPity(),
      signupBonusClaimed: false,
    };
    // Doc lai de tra ve so du + signupBonusClaimed sau khi tang thuong tan thu
    const fresh = await this.usersRepo.findByUid(authUser.uid);
    return fresh ?? { ...base, ...patch, uid: authUser.uid };
  }

  /** Lay ho so cua chinh minh (tu tao neu chua co). */
  async getProfile(authUser: AuthUser): Promise<User> {
    return this.ensureUser(authUser);
  }

  /** Xem ho so cong khai cua user khac. */
  async getPublicProfile(uid: string): Promise<PublicUser> {
    const user = await this.usersRepo.findByUid(uid);
    if (!user) {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
    return this.usersRepo.toPublic(user);
  }

  /** Cap nhat ho so (ten hien thi / avatar / ngay sinh). */
  async updateProfile(uid: string, dto: UpdateUserDto): Promise<User> {
    // Ngay sinh khong duoc o TUONG LAI (DTO chi validate format yyyy-MM-dd)
    if (dto.birthday !== undefined && dto.birthday > dateKey()) {
      throw new BadRequestException('Ngay sinh khong the o tuong lai.');
    }
    // Firestore KHONG nhan class instance (ValidationPipe transform tao ra
    // UpdateUserDto co prototype) -> chuyen ve plain object, bo field undefined
    const patch: Partial<User> = {};
    if (dto.fullName !== undefined) patch.fullName = dto.fullName;
    if (dto.avatar !== undefined) patch.avatar = dto.avatar;
    if (dto.birthday !== undefined) patch.birthday = dto.birthday;
    await this.usersRepo.update(uid, patch);

    // Dong bo len Firebase AUTH (best-effort, 2026-07-26): ten hien thi tren
    // trang admin lay tu Firestore nhung displayName cua Auth cung nen khop
    // (het lech ten giua 2 he thong tan goc).
    if (dto.fullName !== undefined || dto.avatar) {
      const authPatch: { displayName?: string; photoURL?: string } = {};
      if (dto.fullName !== undefined) authPatch.displayName = dto.fullName;
      if (dto.avatar) authPatch.photoURL = dto.avatar;
      await this.firebase
        .auth()
        .updateUser(uid, authPatch)
        .catch((e) =>
          this.logger.warn(`Khong sync duoc displayName len Auth: ${(e as Error).message}`),
        );
    }

    const user = await this.usersRepo.findByUid(uid);
    if (!user) {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
    return user;
  }

  /**
   * Gui FCM cho danh sach uid (gom moi token cua ho) — helper DUY NHAT cho push
   * (gom tu 4 ban sao o moments/messages/coop/friendships, 2026-07-26).
   * Best-effort: KHONG BAO GIO throw; loi chi log warn.
   */
  async pushToUids(
    uids: string[],
    title: string,
    body: string,
    data?: Record<string, string>,
  ): Promise<number> {
    try {
      const unique = [...new Set(uids)].filter(Boolean);
      if (unique.length === 0) {
        return 0;
      }
      const users = await Promise.all(unique.map((id) => this.usersRepo.findByUid(id)));
      const tokens = [...new Set(users.flatMap((u) => u?.fcmTokens ?? []))];
      if (tokens.length === 0) {
        return 0;
      }
      const res = await this.firebase.messaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        ...(data ? { data } : {}),
      });
      this.logger.log(`FCM: gui ${res.successCount}/${tokens.length} thiet bi.`);
      return res.successCount;
    } catch (e) {
      this.logger.warn(`Khong gui duoc FCM: ${(e as Error).message}`);
      return 0;
    }
  }

  async addFcmToken(uid: string, token: string): Promise<void> {
    await this.usersRepo.addFcmToken(uid, token);
  }

  async removeFcmToken(uid: string, token: string): Promise<void> {
    await this.usersRepo.removeFcmToken(uid, token);
  }

  /**
   * Lay (hoac sinh moi) ma ket ban cua user — moi user 1 ma, hieu luc co dinh
   * INVITE_LINK_TTL_DAYS ngay (trong han ai co link deu ket ban duoc, khong gioi han luot).
   * Chua co ma hoac ma da het han -> sinh ma moi + han moi, ma cu vo hieu.
   */
  async getOrCreateInviteCode(uid: string): Promise<{ inviteCode: string; expiresAt: string }> {
    const user = await this.usersRepo.findByUid(uid);
    if (!user) {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
    if (
      user.inviteCode &&
      user.inviteCodeExpiresAt &&
      new Date(user.inviteCodeExpiresAt).getTime() > Date.now()
    ) {
      return { inviteCode: user.inviteCode, expiresAt: user.inviteCodeExpiresAt };
    }
    const inviteCode = randomBytes(6).toString('hex');
    const expiresAt = new Date(
      Date.now() + INVITE_LINK_TTL_DAYS * 24 * 60 * 60 * 1000,
    ).toISOString();
    await this.usersRepo.update(uid, { inviteCode, inviteCodeExpiresAt: expiresAt });
    return { inviteCode, expiresAt };
  }

  async findByInviteCode(inviteCode: string): Promise<User | null> {
    return this.usersRepo.findByInviteCode(inviteCode);
  }

  /**
   * Cap nhat streak ca nhan khi user co hoat dong (mo app + upload moment).
   * Cung ngay: giu nguyen. Hom qua: +1. Cach xa hon: reset ve 1.
   * Duoc goi tu MomentsService khi dang bai (se noi day o buoc sau).
   */
  async registerActivityForStreak(uid: string): Promise<number> {
    const user = await this.usersRepo.findByUid(uid);
    if (!user) {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
    // dateKey CHUNG voi daily quest (common/constants) — 2 he thong phai khop ngay
    const today = dateKey();
    if (user.lastStreakDate === today) {
      return user.personalStreak;
    }
    const yesterday = dateKey(new Date(Date.now() - 24 * 60 * 60 * 1000));
    const nextStreak = user.lastStreakDate === yesterday ? user.personalStreak + 1 : 1;
    await this.usersRepo.update(uid, { personalStreak: nextStreak, lastStreakDate: today });
    return nextStreak;
  }
}
