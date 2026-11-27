/**
 * Hang so nghiep vu (business rules) — lay tu tai lieu phan tich thiet ke.
 * KHONG hardcode cac gia tri nay rai rac trong code.
 */
export const MAX_FRIENDS = 20; // Gioi han ban be moi user
export const MAX_GROUP_SIZE = 20; // Thanh vien toi da 1 nhom chat
export const MAX_VIDEO_SECONDS = 3; // Do dai "anh GIF" (clip ngan, lap vo han, khong tieng) toi da — chot 2026-08-03 (truoc la 5)
export const DAILY_QUESTS_PER_DAY = 3; // 2 quest co dinh (LOGIN + POST_MOMENT) + 1 quest AI (AI_CHALLENGE) — nang tu 2 len 3 theo thiet ke goc (2026-08-15, QUEST_AI_PLAN.md)
export const STREAK_MILESTONES = [3, 7, 14, 30]; // Moc streak ca nhan duoc thuong khung
export const COOP_INVITE_TTL_MINUTES = 5; // Loi moi chup chung qua 5 phut chua tra loi -> het han
export const COOP_SESSION_TTL_MINUTES = 10; // Phien chup chung da ACCEPTED qua 10 phut chua ghep xong -> het han (2 ben bi kill app khong treo mai)
export const COOP_MEDIA_ALLOWED_HOSTS = ['res.cloudinary.com']; // Nua anh coop chi nhan URL tu Cloudinary (server tai ve de ghep — chong SSRF)
export const INVITE_LINK_TTL_DAYS = 30; // Ma moi ket ban hieu luc 30 ngay; het han -> sinh ma moi, ma cu vo hieu (chot 2026-07-19)
export const INVITE_LINK_BASE_URL = 'https://snapget-d8693.web.app/invite/'; // Firebase Hosting cua project — App Links verify duoc (assetlinks.json trong hosting/)

// ==== Cua hang (Shop) & tien te Astrite (2026-11-15 — THAY he thong gacha, xem Snapget/.claude/SHOP_PLAN.md) ====
// Gia MAC DINH khi tao mat hang moi (seed / tu dong niem yet khung SHOP). Admin
// sua gia tung mon tren web — gia that luon doc tu doc `shopItems`, khong tu day.

export const SHOP_DEFAULT_PRICE_FRAME = 320; // Khung anh
export const SHOP_DEFAULT_PRICE_EFFECT = 1600; // Hieu ung touch (= dung thuong tan thu)
export const SHOP_DEFAULT_PRICE_SKIN = 4800; // Skin giao dien

// ==== Nap Astrite qua PayOS (G6 — SHOP_PLAN.md, truoc la GACHA_PLAN.md muc 4) ====

/** Don PENDING qua han nay coi nhu bo (PayOS cung het han link ~ khoang nay). */
export const TOPUP_ORDER_TTL_MINUTES = 30;

/**
 * PayOS gioi han `description` cua link thanh toan 25 KY TU — dai hon la API
 * tra loi 400. Chuoi nay hien trong noi dung chuyen khoan cua nguoi dung.
 */
export const PAYOS_DESCRIPTION_MAX = 25;

export const QUEST_DAILY_ASTRITE = 60; // Thuong khi xong 2/2 quest CO DINH trong ngay (THAY cho mo khung ngau nhien) — quest AI KHONG tinh vao moc nay

// ==== AI Daily Quest (2026-08-15 — xem Snapget/.claude/QUEST_AI_PLAN.md) ====
// Quest thu 3 moi ngay do AI sinh ("Take a photo of a cup"), hoan thanh khi anh
// user DANG LEN FEED chua dung vat the (model tu train xac minh). Thieu env
// AI_SERVICE_URL/AI_SERVICE_API_KEY -> toan bo tinh nang tat em, app chay y het 2 quest.

export const QUEST_AI_ASTRITE = 30; // Thuong RIENG khi xong quest AI (60 cua 2/2 quest co dinh GIU NGUYEN) — toi da 90/ngay
/**
 * 7 QUEST AI ra de — user chot 2026-11-27 sau khi danh gia model y1 (YOLO11s) tren anh that.
 * Key quest (luu o `targetClass` cua quest) -> CAC LOP DUOC CHAP NHAN: anh khop neu BAT KY lop
 * nao vuot nguong rieng cua lop do (gop lop de lan -> tang ti le pass, giam loai nham).
 *   - `person` = co >= 2 NGUOI trong khung (quest "Snap a photo with your friend"): model PHU
 *     YOLO11n COCO goc tren AI service (khong phai lop cua y1), KHONG loc nguoi la/kich thuoc.
 *   - Bo khoi vong quay: clock, backpack, potted plant (van la lop cua model, co so lieu bao cao).
 * Truoc 2026-11-27: 9 lop don (cup, bottle, chair, potted plant, laptop, clock, umbrella,
 * bicycle, motorcycle). Them/bot quest hoac lop trong nhom = sua map nay, KHONG retrain.
 * Quest "nguoi + vat" (vd ban cam o) de sau — se can dieu kien AND, chua lam.
 * KHOP `QUEST_GROUPS` o ml/snapget12/classes.py (danh gia theo quest — 17_eval_quests.py).
 */
export const AI_QUEST_GROUPS = {
  bicycle: ['bicycle', 'motorcycle'],
  laptop: ['laptop', 'keyboard'],
  umbrella: ['umbrella'],
  chair: ['chair'],
  cup: ['cup', 'bottle'],
  book: ['book'],
  person: ['person'],
} as const satisfies Record<string, readonly string[]>;
export type AiQuestClass = keyof typeof AI_QUEST_GROUPS;
/** Key cua 7 quest ra de (thu tu = thu tu khai bao o AI_QUEST_GROUPS). */
export const AI_QUEST_CLASSES = Object.keys(AI_QUEST_GROUPS) as AiQuestClass[];
/**
 * Cac lop AI service phai cham cho 1 quest. Key la (doc quest cu trong Firestore truoc
 * 2026-11-27, vd `clock`, `motorcycle`) -> coi la 1 lop don — van verify duoc.
 */
export function aiQuestTargetClasses(key: string): readonly string[] {
  return (AI_QUEST_GROUPS as Record<string, readonly string[]>)[key] ?? [key];
}
/** Toan bo lop model y1 biet (thu tu = output model, khop ml/snapget12/classes.py) — lop trong AI_QUEST_GROUPS ⊂ list nay ∪ AI_EXTRA_CLASSES. */
export const AI_MODEL_CLASSES = [
  'cup',
  'bottle',
  'book',
  'chair',
  'potted plant',
  'laptop',
  'keyboard',
  'backpack',
  'clock',
  'umbrella',
  'bicycle',
  'motorcycle',
] as const;
/** Lop cua MODEL PHU tren AI service (ngoai 12 lop y1) — `person`: diem = nguoi thu 2 sau NMS (>= 2 nguoi). */
export const AI_EXTRA_CLASSES = ['person'] as const;
export const AI_QUEST_AVOID_RECENT = 3; // Khong lap quest cua 3 ngay gan nhat (7 quest -> moi quest ~4 lan/thang)
export const AI_QUEST_CONTENT_MAX = 80; // content quest AI toi da (ky tu) — LLM tra dai hon thi dung fallback
export const AI_VERIFY_TIMEOUT_MS = 3000; // AI service verify qua 3s -> SKIPPED, khong giu chan response dang bai
export const AI_GENERATE_TIMEOUT_MS = 90_000; // LLM CPU sinh cham — cron goi offline nen cho duoc lau
export const SIGNUP_BONUS_ASTRITE = 1600; // Tang 1 lan khi tao tai khoan (du mua ngay 1 hieu ung hoac 5 khung o Shop)

/** Key ngay (YYYY-MM-DD, UTC) — dung CHUNG cho personal streak va daily quest de 2 he thong khop ngay. */
export const dateKey = (date: Date = new Date()): string => date.toISOString().slice(0, 10);

/** Ten cac collection Firestore (canonical). */
export const Collections = {
  USERS: 'users',
  FRIENDSHIPS: 'friendships',
  POSTS: 'posts', // Moment (bai dang) — giu ten posts de tuong thich app cu
  MESSAGES: 'messages',
  CHAT_GROUPS: 'chatGroups',
  FRAMES: 'frames',
  DAILY_QUESTS: 'dailyQuests',
  USER_QUESTS: 'userQuests',
  COOP_INVITES: 'coopInvites',
  NOTIFICATIONS: 'notifications',
  ADMIN_LOGS: 'adminLogs', // nhat ky hanh dong cua admin (audit log, 2026-07-26)
  // Tien te Astrite + Shop (2026-08-05; gacha -> shop 2026-11-15)
  ASTRITE_TRANSACTIONS: 'astriteTransactions', // so cai: MOI thay doi so du deu ghi 1 dong
  SHOP_ITEMS: 'shopItems', // mat hang ban trong Shop (khung / skin / hieu ung) — admin quan ly
  SHOP_PURCHASES: 'shopPurchases', // lich su mua (1 doc = 1 lan mua, snapshot ten + gia)
  TOPUP_PACKAGES: 'topupPackages', // goi nap (admin quan ly)
  TOPUP_ORDERS: 'topupOrders', // don nap — doc id = orderCode de webhook idempotent
  AI_VERIFICATIONS: 'aiVerifications', // log MOI lan AI xac minh anh quest (ke ca truot) — so lieu accuracy production (2026-08-15)
} as const;

/** Subcollection cua posts. */
export const SubCollections = {
  VIEWS: 'views',
  REACTIONS: 'reactions',
} as const;

/** Role phan quyen. */
export const Roles = {
  USER: 'user',
  ADMIN: 'admin',
} as const;

export type Role = (typeof Roles)[keyof typeof Roles];
