/**
 * Hang so nghiep vu (business rules) — lay tu tai lieu phan tich thiet ke.
 * KHONG hardcode cac gia tri nay rai rac trong code.
 */
export const MAX_FRIENDS = 20; // Gioi han ban be moi user
export const MAX_GROUP_SIZE = 20; // Thanh vien toi da 1 nhom chat
export const MAX_VIDEO_SECONDS = 3; // Do dai "anh GIF" (clip ngan, lap vo han, khong tieng) toi da — chot 2026-08-03 (truoc la 5)
export const STREAK_WINDOW_HOURS = 24; // Qua 24h khong tuong tac -> reset friend streak
export const DAILY_QUESTS_PER_DAY = 2; // 2 quest co dinh/ngay: LOGIN + POST_MOMENT (chot 2026-07-13, khong AI)
export const STREAK_MILESTONES = [3, 7, 14, 30]; // Moc streak ca nhan duoc thuong khung
export const COOP_INVITE_TTL_MINUTES = 5; // Loi moi chup chung qua 5 phut chua tra loi -> het han
export const INVITE_LINK_TTL_DAYS = 30; // Ma moi ket ban hieu luc 30 ngay; het han -> sinh ma moi, ma cu vo hieu (chot 2026-07-19)
export const INVITE_LINK_BASE_URL = 'https://snapget-d8693.web.app/invite/'; // Firebase Hosting cua project — App Links verify duoc (assetlinks.json trong hosting/)

// ==== Gacha & tien te Astrite (chot 2026-08-05 — xem Snapget/.claude/GACHA_PLAN.md) ====
// User chot HARDCODE toan bo cac so nay (khong cho admin sua qua web) — doi so
// thi sua o day roi deploy lai. Popup "Rule gacha" trong app TU SINH tu chinh
// cac gia tri nay nen khong bao gio lech voi ti le dang chay.

export const GACHA_COST_SINGLE = 160; // Astrite cho 1 lan quay
export const GACHA_TEN_TIMES = 10; // So lan quay cua goi x10
export const GACHA_COST_TEN = 1440; // Goi x10 giam 10% (160*10 = 1600 -> 1440)

/** Ti le CO BAN tung bac. Phan con lai (95%) roi vao bac N. */
export const GACHA_RATE_SSR = 0.001; // 0,1% — skin giao dien
export const GACHA_RATE_SR = 0.009; // 0,9% — hieu ung touch
export const GACHA_RATE_R = 0.04; // 4%   — khung anh

/** Bao hiem (pity): quay du N lan khong ra bac do -> ep ra. Chi reset bo dem cua BAC VUA TRUNG. */
export const GACHA_PITY_R = 10;
export const GACHA_PITY_SR = 50;
export const GACHA_PITY_SSR = 100;

/** Astrite hoan lai khi quay trung vat pham da so huu. */
export const GACHA_REFUND_R = 160; // R duoc phep trung bat cu luc nao
export const GACHA_REFUND_SR = 1000; // SR chi trung khi da so huu HET bac SR
export const GACHA_REFUND_SSR = 2000; // SSR chi trung khi da so huu HET bac SSR

/** Bac N tra Astrite ngau nhien trong khoang nay. */
export const GACHA_N_ASTRITE_MIN = 1;
export const GACHA_N_ASTRITE_MAX = 60;

// ==== Nap Astrite qua PayOS (G6 — GACHA_PLAN.md muc 4) ====

/** Don PENDING qua han nay coi nhu bo (PayOS cung het han link ~ khoang nay). */
export const TOPUP_ORDER_TTL_MINUTES = 30;

/**
 * PayOS gioi han `description` cua link thanh toan 25 KY TU — dai hon la API
 * tra loi 400. Chuoi nay hien trong noi dung chuyen khoan cua nguoi dung.
 */
export const PAYOS_DESCRIPTION_MAX = 25;

export const QUEST_DAILY_ASTRITE = 60; // Thuong khi xong 2/2 quest trong ngay (THAY cho mo khung ngau nhien)
export const SIGNUP_BONUS_ASTRITE = 1600; // Tang 1 lan khi tao tai khoan (= 10 lan quay le, cham pity R)

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
  // Gacha & tien te (2026-08-05)
  ASTRITE_TRANSACTIONS: 'astriteTransactions', // so cai: MOI thay doi so du deu ghi 1 dong
  GACHA_ITEMS: 'gachaItems', // catalog vat pham quay ra (admin quan ly)
  GACHA_ROLLS: 'gachaRolls', // lich su quay (1 doc = 1 lan bam nut, x10 van la 1 doc)
  TOPUP_PACKAGES: 'topupPackages', // goi nap (admin quan ly)
  TOPUP_ORDERS: 'topupOrders', // don nap — doc id = orderCode de webhook idempotent
  CONFIG: 'config', // doc cau hinh don le (vd config/gachaBanner)
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
