/**
 * Quest co dinh moi ngay — QUYET DINH 2026-07-13 (xem TODO.md root):
 * KHONG dung AI o giai doan nay, chi co 2 quest:
 *   - LOGIN: dang nhap vao app (tu hoan thanh khi goi GET /quests/today)
 *   - POST_MOMENT: dang 1 anh/video bat ky (tu hoan thanh khi tao moment)
 */
export type QuestType = 'LOGIN' | 'POST_MOMENT';

export const QUEST_TYPES: QuestType[] = ['LOGIN', 'POST_MOMENT'];

/** Noi dung hien thi cho user (tieng Viet co dau — hien tren app). */
export const QUEST_CONTENT: Record<QuestType, string> = {
  LOGIN: 'Đăng nhập vào Snapget',
  POST_MOMENT: 'Đăng một ảnh hoặc video bất kỳ',
};

/** Thuc the Daily_Quest — quest chung cua ca he thong theo ngay. */
export interface DailyQuest {
  /** Doc id co dinh `${releaseDate}_${type}` — lazy-create idempotent. */
  questId: string;
  type: QuestType;
  content: string;
  /** YYYY-MM-DD (UTC). */
  releaseDate: string;
}

/** Thuc the User_Quest — trang thai hoan thanh cua tung user. */
export interface UserQuest {
  questId: string;
  userId: string;
  type: QuestType;
  releaseDate: string;
  /** Khong can AI xac minh nen chi co 1 trang thai. */
  status: 'COMPLETED';
  completedAt: string;
}

/** Quest cua hom nay kem trang thai cua user hien tai (tra ve cho app). */
export interface TodayQuest extends DailyQuest {
  completed: boolean;
  completedAt?: string;
}

/** Ket qua GET /quests/today. */
export interface TodayQuestsResult {
  quests: TodayQuest[];
  /**
   * So Astrite da thuong hom nay khi xong 2/2 quest (doi tu `rewardFrameId`
   * ngay 2026-08-05 — G2 cua GACHA_PLAN).
   * undefined = chua xong 2/2 · number = da nhan · null = ngay cu (thuong khung).
   */
  rewardAstrite?: number | null;
}
