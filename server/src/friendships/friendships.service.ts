import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { MAX_FRIENDS, STREAK_WINDOW_HOURS } from '../common/constants';
import { UsersService } from '../users/users.service';
import { FriendSummary, Friendship } from './entities/friendship.entity';
import { FriendshipsRepository } from './friendships.repository';

@Injectable()
export class FriendshipsService {
  constructor(
    private readonly repo: FriendshipsRepository,
    private readonly usersService: UsersService,
  ) {}

  /** Kiem tra gioi han 20 ban be truoc khi ket ban. */
  async assertUnderLimit(uid: string): Promise<void> {
    const count = await this.repo.countAccepted(uid);
    if (count >= MAX_FRIENDS) {
      throw new BadRequestException(
        `Da dat gioi han ${MAX_FRIENDS} ban be. Hay xoa bot ban cu de them ban moi.`,
      );
    }
  }

  /** Lay invite link cua chinh minh (sinh ma neu chua co). */
  async getInviteLink(uid: string): Promise<{ inviteCode: string; link: string }> {
    const inviteCode = await this.usersService.getOrCreateInviteCode(uid);
    return { inviteCode, link: `https://snapget.app/invite/${inviteCode}` };
  }

  /**
   * Ket ban qua ma moi. Kiem tra: khong tu ket ban, chua la ban, ca 2 phia < 20.
   * Thanh cong -> tao friendship ACCEPTED cho ca hai.
   */
  async connect(currentUid: string, inviteCode: string): Promise<Friendship> {
    const inviter = await this.usersService.findByInviteCode(inviteCode);
    if (!inviter) {
      throw new NotFoundException('Ma moi khong hop le.');
    }
    if (inviter.uid === currentUid) {
      throw new BadRequestException('Khong the tu ket ban voi chinh minh.');
    }

    const existing = await this.repo.findPair(currentUid, inviter.uid);
    if (existing && existing.status === 'ACCEPTED') {
      throw new BadRequestException('Hai ban da la ban be roi.');
    }

    await this.assertUnderLimit(currentUid);
    await this.assertUnderLimit(inviter.uid);

    return this.repo.create(currentUid, inviter.uid);
  }

  /** Danh sach ban be (kem ten/avatar + friend streak). */
  async listMyFriends(uid: string): Promise<FriendSummary[]> {
    const friendships = await this.repo.listAccepted(uid);
    const summaries = await Promise.all(
      friendships.map(async (f) => {
        const friendUid = f.userIds.find((id) => id !== uid) ?? '';
        const profile = await this.usersService.getPublicProfile(friendUid).catch(() => null);
        return {
          uid: friendUid,
          fullName: profile?.fullName ?? 'Nguoi dung',
          avatar: profile?.avatar,
          friendStreak: f.friendStreak,
        };
      }),
    );
    return summaries;
  }

  /** Xoa ban be. */
  async removeFriend(currentUid: string, friendUid: string): Promise<void> {
    const existing = await this.repo.findPair(currentUid, friendUid);
    if (!existing) {
      throw new NotFoundException('Hai ban chua ket ban.');
    }
    await this.repo.delete(currentUid, friendUid);
  }

  /**
   * Ghi nhan tuong tac giua 2 ban -> cap nhat friend streak.
   * Trong 24h ke tu lan truoc: +1 (neu qua ngay); >24h: reset ve 1.
   * Duoc goi tu moments/messages khi co tuong tac (noi day o buoc sau).
   */
  async registerInteraction(a: string, b: string): Promise<void> {
    const pair = await this.repo.findPair(a, b);
    if (!pair || pair.status !== 'ACCEPTED') {
      return;
    }
    const now = new Date();
    const last = pair.lastInteractionAt ? new Date(pair.lastInteractionAt) : null;
    const withinWindow =
      last !== null && now.getTime() - last.getTime() <= STREAK_WINDOW_HOURS * 60 * 60 * 1000;

    let nextStreak: number;
    if (!last) {
      nextStreak = 1;
    } else if (withinWindow) {
      // Cung "phien" tuong tac — chi tang khi sang ngay moi.
      nextStreak = this.isSameDay(last, now) ? pair.friendStreak : pair.friendStreak + 1;
    } else {
      nextStreak = 1; // Qua 24h khong tuong tac -> reset.
    }
    await this.repo.updateStreak(pair.pairId, nextStreak, now.toISOString());
  }

  private isSameDay(a: Date, b: Date): boolean {
    return a.toISOString().slice(0, 10) === b.toISOString().slice(0, 10);
  }
}
