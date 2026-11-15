/**
 * Thuc the User (hop nhat User + Game trong thiet ke CSDL).
 * uid = Firebase Auth uid. password do Firebase Auth giu, KHONG luu o Firestore.
 */
export interface User {
  uid: string;
  email: string;
  fullName: string;
  avatar?: string;
  joinDate: string; // ISO string
  personalStreak: number;
  /** Ngay gan nhat tinh streak ca nhan (YYYY-MM-DD) — de tang/reset streak. */
  lastStreakDate?: string;
  /** Ngay sinh (yyyy-MM-dd), user tu khai trong Settings. */
  birthday?: string;
  /** Ma moi ket ban (dung sinh invite link). */
  inviteCode?: string;
  /** Han cua ma moi (ISO) — qua han thi ma vo hieu, lay link se tu sinh ma moi. */
  inviteCodeExpiresAt?: string;
  unlockedFrames: string[]; // danh sach frameId (User_Frame)
  fcmTokens: string[];

  // ==== Tien te Astrite + vat pham Shop (2026-08-05; doi gacha -> shop 2026-11-15 — SHOP_PLAN.md) ====
  /** So du Astrite. MOI thay doi phai di kem 1 dong trong `astriteTransactions`. */
  astrite: number;
  /** skinId da so huu. Skin 0 (Default) luon dung duoc nen KHONG nam trong mang nay. */
  unlockedSkins: number[];
  /** effectId da so huu. Effect 0 (None) luon dung duoc nen KHONG nam trong mang nay. */
  unlockedEffects: number[];
  /** Da nhan thuong tan thu chua — chong cong 1600 nhieu lan. */
  signupBonusClaimed: boolean;
}

/** Ho so rut gon de tra ve khi xem user khac (khong lo thong tin nhay cam). */
export interface PublicUser {
  uid: string;
  fullName: string;
  avatar?: string;
  personalStreak: number;
}
