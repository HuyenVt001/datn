import { AiQuestClass } from '../../common/constants';

/**
 * Daily quest — 3 quest/ngay theo thiet ke goc (nang tu 2 len 3 ngay 2026-08-15,
 * xem Snapget/.claude/QUEST_AI_PLAN.md):
 *   - LOGIN: dang nhap vao app (tu hoan thanh khi goi GET /quests/today)
 *   - POST_MOMENT: dang 1 anh/video bat ky (tu hoan thanh khi tao moment)
 *   - AI_CHALLENGE: quest do AI sinh ("Chụp một chiếc cốc") — tu hoan thanh khi
 *     anh user DANG LEN FEED duoc model AI xac minh co chua vat the (`targetClass`).
 *     Chi ton tai khi server co cau hinh AI (thieu env -> ngay do chi co 2 quest).
 */
export type QuestType = 'LOGIN' | 'POST_MOMENT' | 'AI_CHALLENGE';

/** 2 quest co dinh — luon co moi ngay, noi dung tinh. */
export type FixedQuestType = Exclude<QuestType, 'AI_CHALLENGE'>;
export const FIXED_QUEST_TYPES: FixedQuestType[] = ['LOGIN', 'POST_MOMENT'];

/** Tat ca loai quest (thu tu hien thi tren app). */
export const QUEST_TYPES: QuestType[] = [...FIXED_QUEST_TYPES, 'AI_CHALLENGE'];

/** Noi dung hien thi cho user (tieng Viet co dau — hien tren app). Quest AI lay tu LLM/template. */
export const QUEST_CONTENT: Record<FixedQuestType, string> = {
  LOGIN: 'Đăng nhập vào Snapget',
  POST_MOMENT: 'Đăng một ảnh hoặc video bất kỳ',
};

/**
 * Nguon sinh noi dung quest AI. Tu 2026-08-16 THUC TE luon la FALLBACK (user chot bo
 * LLM sinh quest — quest lay tu bo mau 72 cau); giu lai gia tri LLM lam diem cam san.
 */
export type AiQuestSource = 'LLM' | 'FALLBACK';

/** Thuc the Daily_Quest — quest chung cua ca he thong theo ngay. */
export interface DailyQuest {
  /** Doc id co dinh `${releaseDate}_${type}` — lazy-create idempotent. */
  questId: string;
  type: QuestType;
  content: string;
  /** YYYY-MM-DD (UTC). */
  releaseDate: string;
  /** CHI quest AI_CHALLENGE: vat the phai co trong anh (1 trong AI_QUEST_CLASSES). */
  targetClass?: AiQuestClass;
  /** CHI quest AI_CHALLENGE: noi dung do LLM viet hay template. */
  source?: AiQuestSource;
  /** CHI quest AI_CHALLENGE: thoi diem sinh (ISO). */
  generatedAt?: string;
}

/** Thuc the User_Quest — trang thai hoan thanh cua tung user. */
export interface UserQuest {
  questId: string;
  userId: string;
  type: QuestType;
  releaseDate: string;
  /**
   * Chi 1 trang thai ke ca voi quest AI: anh khong khop thi quest don gian la
   * CHUA XONG (khong co doc), khong can PENDING/REJECTED (QUEST_AI_PLAN muc 0).
   */
  status: 'COMPLETED';
  completedAt: string;
  /** CHI quest AI_CHALLENGE: moment nao hoan thanh quest (audit + so lieu bao cao). */
  momentId?: string;
  /** CHI quest AI_CHALLENGE: diem sigmoid cua targetClass luc khop. */
  aiScore?: number;
  /** CHI quest AI_CHALLENGE: phien ban model da xac minh. */
  modelVersion?: string;
}

/** Truong bo sung khi hoan thanh quest AI (ghi kem vao UserQuest). */
export type AiCompletionMeta = Pick<UserQuest, 'momentId' | 'aiScore' | 'modelVersion'>;

/** Quest cua hom nay kem trang thai cua user hien tai (tra ve cho app). */
export interface TodayQuest extends DailyQuest {
  completed: boolean;
  completedAt?: string;
}

/** Ket qua GET /quests/today. */
export interface TodayQuestsResult {
  quests: TodayQuest[];
  /**
   * So Astrite da thuong hom nay khi xong 2/2 quest CO DINH (doi tu `rewardFrameId`
   * ngay 2026-08-05 — G2 cua GACHA_PLAN). Quest AI thuong rieng (+30), khong tinh vao day.
   * undefined = chua xong 2/2 · number = da nhan · null = ngay cu (thuong khung).
   */
  rewardAstrite?: number | null;
}

/**
 * Ket qua AI xac minh anh vua dang so voi quest AI hom nay — gan vao response
 * POST /moments (field `aiQuest`, chi co khi hom nay co quest AI & user chua xong).
 *   MATCHED     : anh co vat the -> quest xong + da cong QUEST_AI_ASTRITE
 *   NOT_MATCHED : anh khong co vat the -> bai VAN dang, quest chua tick, dang bai khac de thu lai
 *   SKIPPED     : AI service loi/timeout -> bo qua, khong anh huong dang bai
 */
export interface AiQuestResult {
  result: 'MATCHED' | 'NOT_MATCHED' | 'SKIPPED';
  /** Diem sigmoid [0,1] cua targetClass (khong co khi SKIPPED). */
  score?: number;
  /** Noi dung quest AI (de app hien toast co ngu canh). */
  questContent?: string;
}

/** Ket qua sinh quest AI cho 1 ngay (cron endpoint). */
export interface AiQuestGenerationResult {
  date: string;
  quest: DailyQuest;
  /** true = vua tao moi trong lan goi nay; false = da co tu truoc (idempotent). */
  created: boolean;
}
