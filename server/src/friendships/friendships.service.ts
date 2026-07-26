import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { INVITE_LINK_BASE_URL, MAX_FRIENDS, STREAK_WINDOW_HOURS } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { PublicUser, User } from '../users/entities/user.entity';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { FriendRequestSummary, FriendSummary, Friendship } from './entities/friendship.entity';
import { FriendshipsRepository } from './friendships.repository';

@Injectable()
export class FriendshipsService {
  private readonly logger = new Logger(FriendshipsService.name);

  constructor(
    private readonly repo: FriendshipsRepository,
    private readonly usersService: UsersService,
    private readonly usersRepo: UsersRepository,
    private readonly firebase: FirebaseService,
  ) {}

  /**
   * Lay invite link cua chinh minh — moi user 1 link, hieu luc 30 ngay
   * (INVITE_LINK_TTL_DAYS); het han thi usersService tu sinh ma moi.
   */
  async getInviteLink(
    uid: string,
  ): Promise<{ inviteCode: string; link: string; expiresAt: string }> {
    const { inviteCode, expiresAt } = await this.usersService.getOrCreateInviteCode(uid);
    return { inviteCode, link: `${INVITE_LINK_BASE_URL}${inviteCode}`, expiresAt };
  }

  /** Tim nguoi moi tu ma + kiem tra han — ma het han coi nhu vo hieu (phai xin link moi). */
  private async resolveInviter(inviteCode: string): Promise<{ inviter: User; expiresAt: string }> {
    const inviter = await this.usersService.findByInviteCode(inviteCode);
    if (!inviter) {
      throw new NotFoundException('Ma moi khong hop le.');
    }
    const expiresAt = inviter.inviteCodeExpiresAt;
    if (!expiresAt || new Date(expiresAt).getTime() <= Date.now()) {
      throw new BadRequestException('Link moi da het han. Hay xin ban be link moi nhe.');
    }
    return { inviter, expiresAt };
  }

  /**
   * Thong tin nguoi moi tu ma moi (ten + avatar + han link) — app hien dialog
   * "Ket ban voi X?" de nguoi bam link XAC NHAN truoc khi goi connect.
   */
  async getInviteInfo(inviteCode: string): Promise<PublicUser & { expiresAt: string }> {
    const { inviter, expiresAt } = await this.resolveInviter(inviteCode);
    const profile = await this.usersService.getPublicProfile(inviter.uid);
    return { ...profile, expiresAt };
  }

  /**
   * GUI LOI MOI ket ban qua ma moi (thiet ke 2 buoc — chot 2026-07-19):
   * nguoi bam link chi TAO loi moi PENDING; CHU LINK phai accept moi thanh ban.
   * Ngoai le: 2 ben cung moi nhau (PENDING nguoc chieu) -> ACCEPTED luon.
   * Kiem tra ma con han + khong tu moi minh; phan con lai (cap da ton tai +
   * gioi han 20 ca 2 phia + tao doc) chay trong MOT transaction o repo.
   */
  async connect(currentUid: string, inviteCode: string): Promise<Friendship> {
    const { inviter } = await this.resolveInviter(inviteCode);
    if (inviter.uid === currentUid) {
      throw new BadRequestException('Khong the tu ket ban voi chinh minh.');
    }

    const result = await this.repo.createPendingRequest(currentUid, inviter.uid, MAX_FRIENDS);
    switch (result.outcome) {
      case 'REQUESTED':
        void this.notifyUser(
          inviter.uid,
          'Loi moi ket ban moi',
          `${await this.displayName(currentUid)} muon ket ban voi ban — mo Snapget de xac nhan!`,
          { type: 'FRIEND_REQUEST', requesterUid: currentUid },
        );
        return result.friendship;
      case 'MUTUAL_ACCEPTED':
        // Nguoi kia da moi minh truoc -> 2 ben cung muon, thanh ban ngay
        void this.notifyUser(
          inviter.uid,
          'Ket ban thanh cong',
          `${await this.displayName(currentUid)} da chap nhan loi moi ket ban cua ban! 🎉`,
          { type: 'FRIEND_ACCEPTED', friendUid: currentUid },
        );
        return result.friendship;
      case 'ALREADY_FRIENDS':
        throw new BadRequestException('Hai ban da la ban be roi.');
      case 'ALREADY_REQUESTED':
        throw new BadRequestException('Ban da gui loi moi truoc do roi — cho xac nhan nhe.');
      case 'LIMIT_REQUESTER':
        throw new BadRequestException(
          `Da dat gioi han ${MAX_FRIENDS} ban be. Hay xoa bot ban cu de them ban moi.`,
        );
      case 'LIMIT_INVITER':
        throw new BadRequestException(`Nguoi moi da du ${MAX_FRIENDS} ban be.`);
    }
  }

  /** Danh sach loi moi ket ban dang cho MINH (chu link) xac nhan. */
  async listRequests(uid: string): Promise<FriendRequestSummary[]> {
    const pending = await this.repo.listPendingRequests(uid);
    return Promise.all(
      pending.map(async (f) => {
        const requesterUid = f.requesterUid ?? '';
        const profile = await this.usersService.getPublicProfile(requesterUid).catch(() => null);
        return {
          uid: requesterUid,
          fullName: profile?.fullName ?? 'Nguoi dung',
          avatar: profile?.avatar,
          requestedAt: f.createdAt,
        };
      }),
    );
  }

  /** Chu link CHAP NHAN loi moi -> thanh ban (transaction kiem tra lai gioi han 20). */
  async acceptRequest(currentUid: string, requesterUid: string): Promise<Friendship> {
    const result = await this.repo.acceptRequest(currentUid, requesterUid, MAX_FRIENDS);
    switch (result.outcome) {
      case 'ACCEPTED':
        void this.notifyUser(
          requesterUid,
          'Ket ban thanh cong',
          `${await this.displayName(currentUid)} da chap nhan loi moi ket ban cua ban! 🎉`,
          { type: 'FRIEND_ACCEPTED', friendUid: currentUid },
        );
        return result.friendship;
      case 'NOT_FOUND':
        throw new NotFoundException('Khong tim thay loi moi ket ban nay.');
      case 'LIMIT_CURRENT':
        throw new BadRequestException(
          `Da dat gioi han ${MAX_FRIENDS} ban be. Hay xoa bot ban cu roi chap nhan lai.`,
        );
      case 'LIMIT_REQUESTER':
        throw new BadRequestException(`Nguoi gui loi moi da du ${MAX_FRIENDS} ban be.`);
    }
  }

  /** Chu link TU CHOI loi moi (xoa im lang — khong bao cho nguoi gui). */
  async declineRequest(currentUid: string, requesterUid: string): Promise<void> {
    const ok = await this.repo.declineRequest(currentUid, requesterUid);
    if (!ok) {
      throw new NotFoundException('Khong tim thay loi moi ket ban nay.');
    }
  }

  /** Ten hien thi cho noi dung FCM. */
  private async displayName(uid: string): Promise<string> {
    const user = await this.usersRepo.findByUid(uid).catch(() => null);
    return user?.fullName ?? 'Mot nguoi ban';
  }

  /** Gui FCM best-effort (khong bao gio throw — loi chi log warn). */
  private async notifyUser(
    uid: string,
    title: string,
    body: string,
    data: Record<string, string>,
  ): Promise<void> {
    try {
      const user = await this.usersRepo.findByUid(uid);
      const tokens = user?.fcmTokens ?? [];
      if (tokens.length === 0) {
        return;
      }
      await this.firebase.messaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        data,
      });
    } catch (e) {
      this.logger.warn(`Khong gui duoc FCM loi moi ket ban: ${(e as Error).message}`);
    }
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
