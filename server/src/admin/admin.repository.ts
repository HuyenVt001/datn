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
  /** So luot hoan thanh quest hom nay (them boi AdminService, khong dem o day). */
  questCompletionsToday?: number;
  /** **(2026-08-05)** So luot BAM NUT quay gacha (x10 tinh la 1) — AdminService dien tu GachaService. */
  gachaRollsToday?: number;
  gachaRollsTotal?: number;
}

/** 1 diem du lieu cua bieu do thong ke theo ngay tren dashboard. */
export interface DailyStat {
  /** Ngay dang UTC YYYY-MM-DD (khop dateKey cua streak/quest). */
  date: string;
  moments: number;
  /** So user dang ky moi trong ngay (AdminService dem tu Firebase Auth). */
  newUsers: number;
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

  /**
   * Dem moment theo tung ngay trong `days` ngay gan nhat (UTC).
   * Moi ngay 1 count() voi 2 range filter tren CUNG field postTime — khong can
   * composite index. `newUsers` de 0, AdminService dien tu Firebase Auth.
   */
  async countMomentsByDay(days: number): Promise<DailyStat[]> {
    const today = new Date();
    const dayKeys = Array.from({ length: days }, (_, i) => {
      const d = new Date(today);
      d.setUTCDate(d.getUTCDate() - (days - 1 - i));
      return d.toISOString().slice(0, 10);
    });

    const counts = await Promise.all(
      dayKeys.map(async (day) => {
        const next = new Date(`${day}T00:00:00.000Z`);
        next.setUTCDate(next.getUTCDate() + 1);
        const snap = await this.col(Collections.POSTS)
          .where('postTime', '>=', `${day}T00:00:00.000Z`)
          .where('postTime', '<', `${next.toISOString().slice(0, 10)}T00:00:00.000Z`)
          .count()
          .get();
        return snap.data().count;
      }),
    );

    return dayKeys.map((date, i) => ({ date, moments: counts[i], newUsers: 0 }));
  }

  /**
   * fullName Firestore cua TOAN BO user (1 query ca collection — quy mo DATN).
   * Dung de merge vao danh sach Auth TRUOC khi search: ten hien thi tren trang
   * admin la ten Firestore, search phai chay tren dung ten do (user doi ten
   * trong app chi ghi Firestore, khong sync displayName len Auth).
   */
  async getAllFullNames(): Promise<Map<string, string>> {
    return new Map([...(await this.getAllUserSummaries())].map(([uid, s]) => [uid, s.fullName]));
  }

  /**
   * **(2026-08-05)** Ten + so du Astrite cua TOAN BO user — 1 query ca
   * collection, dung cho danh sach user cua trang admin (khong doc 2 lan).
   */
  async getAllUserSummaries(): Promise<Map<string, { fullName: string; astrite: number }>> {
    const snap = await this.col(Collections.USERS).get();
    const result = new Map<string, { fullName: string; astrite: number }>();
    for (const doc of snap.docs) {
      const data = doc.data();
      result.set(doc.id, {
        fullName: data.fullName ?? data.name ?? '',
        astrite: typeof data.astrite === 'number' ? data.astrite : 0,
      });
    }
    return result;
  }
}
