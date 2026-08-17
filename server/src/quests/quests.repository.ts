import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { AiVerification, AiVerificationRecord } from './entities/ai-verification.entity';
import {
  AiCompletionMeta,
  DailyQuest,
  FIXED_QUEST_TYPES,
  QUEST_CONTENT,
  QUEST_TYPES,
  QuestType,
  UserQuest,
} from './entities/quest.entity';

/** Doc danh dau "da xet thuong cua ngay" — luu chung collection userQuests. */
const DAILY_REWARD_TYPE = 'DAILY_REWARD';

/** NOI DUY NHAT cham Firestore cho daily quests + user quests + log AI verify. */
@Injectable()
export class QuestsRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get dailyQuests() {
    return this.firebase.firestore().collection(Collections.DAILY_QUESTS);
  }

  private get userQuests() {
    return this.firebase.firestore().collection(Collections.USER_QUESTS);
  }

  private get aiVerifications() {
    return this.firebase.firestore().collection(Collections.AI_VERIFICATIONS);
  }

  // Doc id co dinh: 2 request dong thoi ghi cung doc -> khong tao trung (khong can transaction).
  private dailyQuestId(date: string, type: QuestType): string {
    return `${date}_${type}`;
  }

  private userQuestId(date: string, uid: string, type: QuestType): string {
    return `${date}_${uid}_${type}`;
  }

  private rewardId(date: string, uid: string): string {
    return `${date}_${uid}_${DAILY_REWARD_TYPE}`;
  }

  /**
   * Lazy-create 2 quest CO DINH cua ngay (idempotent), tra ve danh sach 2 quest do.
   * Quest AI (AI_CHALLENGE) khong sinh o day — xem [getAiQuest]/[createAiQuestIfAbsent].
   */
  async ensureDailyQuests(date: string): Promise<DailyQuest[]> {
    const quests: DailyQuest[] = FIXED_QUEST_TYPES.map((type) => ({
      questId: this.dailyQuestId(date, type),
      type,
      content: QUEST_CONTENT[type],
      releaseDate: date,
    }));

    const batch = this.firebase.firestore().batch();
    for (const quest of quests) {
      const { questId, ...data } = quest;
      batch.set(this.dailyQuests.doc(questId), data, { merge: true });
    }
    await batch.commit();
    return quests;
  }

  /** Quest AI cua ngay (null = chua sinh — cron chua chay hoac AI tat). */
  async getAiQuest(date: string): Promise<DailyQuest | null> {
    const id = this.dailyQuestId(date, 'AI_CHALLENGE');
    const snap = await this.dailyQuests.doc(id).get();
    if (!snap.exists) {
      return null;
    }
    return { questId: id, ...(snap.data() as Omit<DailyQuest, 'questId'>) };
  }

  /**
   * Tao quest AI cua ngay bang create() ATOMIC — 2 luong sinh dong thoi (cron +
   * user mo app cung luc) thi chi 1 luong tao duoc, luong con lai nhan doc da co.
   * Tra ve { quest, created }: created=false => quest la doc DA TON TAI (khong ghi de).
   */
  async createAiQuestIfAbsent(
    quest: Omit<DailyQuest, 'questId'>,
  ): Promise<{ quest: DailyQuest; created: boolean }> {
    const id = this.dailyQuestId(quest.releaseDate, 'AI_CHALLENGE');
    try {
      await this.dailyQuests.doc(id).create(quest);
      return { quest: { questId: id, ...quest }, created: true };
    } catch (e) {
      // CHI nuot ALREADY_EXISTS (gRPC code 6) — doc lai doc da co
      if ((e as { code?: number }).code === 6) {
        const existing = await this.getAiQuest(quest.releaseDate);
        if (existing) {
          return { quest: existing, created: false };
        }
      }
      throw e;
    }
  }

  /**
   * targetClass cua quest AI trong `days` ngay TRUOC `date` (bo qua ngay chua co) —
   * de LLM/fallback khong lap vat the gan day. Doc theo id co dinh, 1 lan getAll.
   */
  async getRecentAiTargets(date: string, days: number): Promise<string[]> {
    if (days <= 0) {
      return [];
    }
    const base = new Date(`${date}T00:00:00.000Z`);
    const refs = Array.from({ length: days }, (_, i) => {
      const d = new Date(base);
      d.setUTCDate(base.getUTCDate() - (i + 1));
      return this.dailyQuests.doc(this.dailyQuestId(d.toISOString().slice(0, 10), 'AI_CHALLENGE'));
    });
    const snaps = await this.firebase.firestore().getAll(...refs);
    return snaps
      .filter((s) => s.exists)
      .map((s) => s.data()?.targetClass as string | undefined)
      .filter((c): c is string => typeof c === 'string' && c.length > 0);
  }

  /** Trang thai quest cua user trong ngay: map type -> UserQuest (thieu = chua xong). */
  async getUserQuests(date: string, uid: string): Promise<Map<QuestType, UserQuest>> {
    const refs = QUEST_TYPES.map((type) => this.userQuests.doc(this.userQuestId(date, uid, type)));
    const snaps = await this.firebase.firestore().getAll(...refs);

    const result = new Map<QuestType, UserQuest>();
    snaps.forEach((snap, index) => {
      if (snap.exists) {
        result.set(QUEST_TYPES[index], snap.data() as UserQuest);
      }
    });
    return result;
  }

  /** User da xong 1 quest cu the trong ngay chua (1 lan doc thay vi getAll ca 3). */
  async hasCompletedQuest(date: string, uid: string, type: QuestType): Promise<boolean> {
    const snap = await this.userQuests.doc(this.userQuestId(date, uid, type)).get();
    return snap.exists;
  }

  /**
   * Danh dau hoan thanh quest. Tra ve true neu la LAN DAU hoan thanh.
   * Dung create() ATOMIC (fail neu doc da ton tai) thay vi get-roi-set —
   * 2 request dong thoi thi chi 1 request nhan true.
   * `meta`: truong bo sung cho quest AI (momentId/aiScore/modelVersion).
   */
  async completeUserQuest(
    date: string,
    uid: string,
    type: QuestType,
    meta?: AiCompletionMeta,
  ): Promise<boolean> {
    const ref = this.userQuests.doc(this.userQuestId(date, uid, type));
    const userQuest: UserQuest = {
      questId: this.dailyQuestId(date, type),
      userId: uid,
      type,
      releaseDate: date,
      status: 'COMPLETED',
      completedAt: new Date().toISOString(),
      ...(meta ?? {}),
    };
    try {
      await ref.create(userQuest);
      return true;
    } catch (e) {
      // CHI nuot ALREADY_EXISTS (gRPC code 6). Loi khac (mat mang, DEADLINE...)
      // phai nem len — nuot het thi hoan thanh quest bi mat im lang.
      if ((e as { code?: number }).code === 6) {
        return false;
      }
      throw e;
    }
  }

  /**
   * Xoa doc hoan thanh quest — CHI dung de TRA LAI khi buoc thuong quest AI fail
   * giua chung (mirror deleteDailyReward), de lan dang sau thu lai duoc.
   */
  async deleteUserQuest(date: string, uid: string, type: QuestType): Promise<void> {
    await this.userQuests.doc(this.userQuestId(date, uid, type)).delete();
  }

  /**
   * So Astrite da thuong cho user trong ngay:
   * undefined = chua xet thuong · null = da xet nhung chua cong xong · number = so Astrite da cong.
   *
   * Doc CU (truoc 2026-08-05) giu `frameId` thay vi `astrite` — doc do tra ve
   * null (da xet thuong roi), dung y nghia: hom do da nhan thuong (khung) roi.
   */
  async getDailyReward(date: string, uid: string): Promise<number | null | undefined> {
    const snap = await this.userQuests.doc(this.rewardId(date, uid)).get();
    if (!snap.exists) {
      return undefined;
    }
    return (snap.data()?.astrite as number | undefined) ?? null;
  }

  /**
   * CLAIM quyen xet thuong cua ngay bang create() ATOMIC — 2 luong hoan thanh
   * quest dong thoi (LOGIN + POST_MOMENT cung luc) thi chi 1 luong claim duoc,
   * tranh cong thuong 2 lan cho 1 ngay. Tra ve false neu ngay nay da claim roi.
   */
  async tryClaimDailyReward(date: string, uid: string): Promise<boolean> {
    try {
      await this.userQuests.doc(this.rewardId(date, uid)).create({
        userId: uid,
        releaseDate: date,
        type: DAILY_REWARD_TYPE,
        astrite: null,
        createdAt: new Date().toISOString(),
      });
      return true;
    } catch (e) {
      // CHI nuot ALREADY_EXISTS (gRPC code 6) — loi khac phai nem len
      if ((e as { code?: number }).code === 6) {
        return false;
      }
      throw e;
    }
  }

  /** Ghi so Astrite da thuong sau khi cong vao vi thanh cong. */
  async setDailyRewardAstrite(date: string, uid: string, amount: number): Promise<void> {
    await this.userQuests.doc(this.rewardId(date, uid)).set({ astrite: amount }, { merge: true });
  }

  /** Xoa doc claim — dung de TRA LAI quyen xet thuong khi buoc thuong fail giua chung. */
  async deleteDailyReward(date: string, uid: string): Promise<void> {
    await this.userQuests.doc(this.rewardId(date, uid)).delete();
  }

  /** Dem so luot hoan thanh quest trong ngay (cho admin stats) — khong tinh doc thuong. */
  async countCompletionsByDate(date: string): Promise<number> {
    const snap = await this.userQuests.where('releaseDate', '==', date).get();
    return snap.docs.filter((d) => d.data().type !== DAILY_REWARD_TYPE).length;
  }

  /** Log 1 lan AI verify (collection aiVerifications) — caller ghi best-effort. */
  async addAiVerification(entry: AiVerification): Promise<void> {
    // Firestore khong nhan undefined -> loc bo truoc khi ghi
    const clean = Object.fromEntries(Object.entries(entry).filter(([, v]) => v !== undefined));
    await this.aiVerifications.add(clean);
  }

  /**
   * Log verify moi nhat (cho trang admin) — orderBy 1 field, khong can composite index;
   * loc outcome/date trong bo nho o service (quy mo DATN, `limit` chan tran).
   */
  async listAiVerifications(limit: number): Promise<AiVerificationRecord[]> {
    const snap = await this.aiVerifications.orderBy('createdAt', 'desc').limit(limit).get();
    return snap.docs.map((d) => ({ id: d.id, ...(d.data() as AiVerification) }));
  }

  /** Toan bo log verify cua 1 ngay (where 1 field) — cho thong ke dashboard. */
  async listAiVerificationsByDate(date: string): Promise<AiVerificationRecord[]> {
    const snap = await this.aiVerifications.where('date', '==', date).get();
    return snap.docs.map((d) => ({ id: d.id, ...(d.data() as AiVerification) }));
  }
}
