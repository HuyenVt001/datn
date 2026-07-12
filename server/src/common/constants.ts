/**
 * Hang so nghiep vu (business rules) — lay tu tai lieu phan tich thiet ke.
 * KHONG hardcode cac gia tri nay rai rac trong code.
 */
export const MAX_FRIENDS = 20; // Gioi han ban be moi user
export const MAX_GROUP_SIZE = 20; // Thanh vien toi da 1 nhom chat
export const MAX_VIDEO_SECONDS = 5; // Do dai video ngan toi da
export const STREAK_WINDOW_HOURS = 24; // Qua 24h khong tuong tac -> reset friend streak
export const DAILY_QUESTS_PER_DAY = 3; // So daily quest he thong tao moi ngay

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
  NOTIFICATIONS: 'notifications',
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
