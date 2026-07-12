import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';

/** So lieu thong ke tong quan cho dashboard admin. */
export interface AdminStats {
  users: number;
  moments: number;
  momentsToday: number;
  messages: number;
  friendships: number;
  chatGroups: number;
}

/** NOI DUY NHAT cham Firestore cho module admin (dem aggregate cross-domain). */
@Injectable()
export class AdminRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private col(name: string) {
    return this.firebase.firestore().collection(name);
  }

  private async count(name: string): Promise<number> {
    const snap = await this.col(name).count().get();
    return snap.data().count;
  }

  /** Thong ke tong quan. momentsToday dung range filter 1 field (khong can composite index). */
  async getStats(): Promise<AdminStats> {
    const todayStart = `${new Date().toISOString().slice(0, 10)}T00:00:00.000Z`;
    const [users, moments, messages, friendships, chatGroups, momentsTodaySnap] = await Promise.all(
      [
        this.count(Collections.USERS),
        this.count(Collections.POSTS),
        this.count(Collections.MESSAGES),
        this.count(Collections.FRIENDSHIPS),
        this.count(Collections.CHAT_GROUPS),
        this.col(Collections.POSTS).where('postTime', '>=', todayStart).count().get(),
      ],
    );
    return {
      users,
      moments,
      momentsToday: momentsTodaySnap.data().count,
      messages,
      friendships,
      chatGroups,
    };
  }

  /** Lay fullName tu Firestore cho danh sach uid (enrich list user cua Auth). */
  async getFullNames(uids: string[]): Promise<Map<string, string>> {
    const result = new Map<string, string>();
    if (uids.length === 0) {
      return result;
    }
    const snaps = await Promise.all(uids.map((uid) => this.col(Collections.USERS).doc(uid).get()));
    for (const snap of snaps) {
      if (snap.exists) {
        const data = snap.data() ?? {};
        result.set(snap.id, data.fullName ?? data.name ?? '');
      }
    }
    return result;
  }
}
