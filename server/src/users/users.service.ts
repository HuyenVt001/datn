import { Injectable, NotFoundException } from '@nestjs/common';
import { randomBytes } from 'crypto';
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
    await this.usersRepo.update(uid, dto);
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

  /** Lay (hoac sinh moi) ma ket ban cua user. */
  async getOrCreateInviteCode(uid: string): Promise<string> {
    const user = await this.usersRepo.findByUid(uid);
    if (!user) {
      throw new NotFoundException('Khong tim thay nguoi dung.');
    }
    if (user.inviteCode) {
      return user.inviteCode;
    }
    const inviteCode = randomBytes(6).toString('hex');
    await this.usersRepo.update(uid, { inviteCode });
    return inviteCode;
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
    const today = this.dateKey(new Date());
    if (user.lastStreakDate === today) {
      return user.personalStreak;
    }
    const yesterday = this.dateKey(new Date(Date.now() - 24 * 60 * 60 * 1000));
    const nextStreak = user.lastStreakDate === yesterday ? user.personalStreak + 1 : 1;
    await this.usersRepo.update(uid, { personalStreak: nextStreak, lastStreakDate: today });
    return nextStreak;
  }

  /** YYYY-MM-DD theo UTC. */
  private dateKey(d: Date): string {
    return d.toISOString().slice(0, 10);
  }
}
