import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import {
  DailyQuest,
  QUEST_CONTENT,
  QUEST_TYPES,
  QuestType,
  UserQuest,
} from './entities/quest.entity';

/** Doc danh dau "da thuong khung cua ngay" — luu chung collection userQuests. */
const DAILY_REWARD_TYPE = 'DAILY_REWARD';

/** NOI DUY NHAT cham Firestore cho daily quests + user quests. */
@Injectable()
export class QuestsRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get dailyQuests() {
    return this.firebase.firestore().collection(Collections.DAILY_QUESTS);
  }

  private get userQuests() {
    return this.firebase.firestore().collection(Collections.USER_QUESTS);
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

  /** Lazy-create 2 quest co dinh cua ngay (idempotent), tra ve danh sach quest. */
  async ensureDailyQuests(date: string): Promise<DailyQuest[]> {
    const quests: DailyQuest[] = QUEST_TYPES.map((type) => ({
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

  /**
   * Danh dau hoan thanh quest. Tra ve true neu la LAN DAU hoan thanh.
   * Dung create() ATOMIC (fail neu doc da ton tai) thay vi get-roi-set —
   * 2 request dong thoi thi chi 1 request nhan true.
   */
  async completeUserQuest(date: string, uid: string, type: QuestType): Promise<boolean> {
    const ref = this.userQuests.doc(this.userQuestId(date, uid, type));
    const userQuest: UserQuest = {
      questId: this.dailyQuestId(date, type),
      userId: uid,
      type,
      releaseDate: date,
      status: 'COMPLETED',
      completedAt: new Date().toISOString(),
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
   * Khung da thuong cho user trong ngay:
   * undefined = chua xet thuong · null = da xet nhung het khung · string = frameId da thuong.
   */
  async getDailyReward(date: string, uid: string): Promise<string | null | undefined> {
    const snap = await this.userQuests.doc(this.rewardId(date, uid)).get();
    if (!snap.exists) {
      return undefined;
    }
    return (snap.data()?.frameId as string | undefined) ?? null;
  }

  /**
   * CLAIM quyen xet thuong cua ngay bang create() ATOMIC — 2 luong hoan thanh
   * quest dong thoi (LOGIN + POST_MOMENT cung luc) thi chi 1 luong claim duoc,
   * tranh thuong 2 khung cho 1 ngay. Tra ve false neu ngay nay da claim roi.
   */
  async tryClaimDailyReward(date: string, uid: string): Promise<boolean> {
    try {
      await this.userQuests.doc(this.rewardId(date, uid)).create({
        userId: uid,
        releaseDate: date,
        type: DAILY_REWARD_TYPE,
        frameId: null,
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

  /** Ghi frameId da thuong sau khi claim (null = het khung de thuong). */
  async setDailyRewardFrame(date: string, uid: string, frameId: string | null): Promise<void> {
    await this.userQuests.doc(this.rewardId(date, uid)).set({ frameId }, { merge: true });
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
}
