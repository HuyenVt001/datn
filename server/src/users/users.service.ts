import { Injectable, NotFoundException } from '@nestjs/common';
import { randomBytes } from 'crypto';
import { dateKey, INVITE_LINK_TTL_DAYS } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { UpdateUserDto } from './dto/update-user.dto';
import { PublicUser, User } from './entities/user.entity';
import { UsersRepository } from './users.repository';

@Injectable()
export class UsersService {
  constructor(private readonly usersRepo: UsersRepository) {}

  /**
   * Dam bao user doc ton tai (goi khi dang nhap lan dau).
   * Neu chua co -> tao moi tu thong tin Firebase Auth.
   */
  async ensureUser(authUser: AuthUser): Promise<User> {
    const existing = await this.usersRepo.findByUid(authUser.uid);
    if (existing) {
      return existing;
    }
    const now = new Date().toISOString();
    const user: User = {
      uid: authUser.uid,
      email: authUser.email ?? '',
      fullName: (authUser.name as string) ?? authUser.email?.split('@')[0] ?? 'Snapget user',
      avatar: (authUser.picture as string) ?? undefined,
      joinDate: now,
      personalStreak: 0,
      unlockedFrames: [],
      fcmTokens: [],
    };
    await this.usersRepo.create(user);
    return user;
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

  /** Cap nhat ho so (ten hien thi / avatar). */
  async updateProfile(uid: string, dto: UpdateUserDto): Promise<User> {
    // Firestore KHONG nhan class instance (ValidationPipe transform tao ra
    // UpdateUserDto co prototype) -> chuyen ve plain object, bo field undefined
    const patch: Partial<User> = {};
    if (dto.fullName !== undefined) patch.fullName = dto.fullName;
    if (dto.avatar !== undefined) patch.avatar = dto.avatar;
    await this.usersRepo.update(uid, patch);
    const user = await this.usersRepo.findByUid(uid);
    if (!user) {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
    return user;
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
