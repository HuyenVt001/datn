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
