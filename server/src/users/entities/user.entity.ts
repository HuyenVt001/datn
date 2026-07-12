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
  /** Ma moi ket ban (dung sinh invite link). */
  inviteCode?: string;
  unlockedFrames: string[]; // danh sach frameId (User_Frame)
  fcmTokens: string[];
}

/** Ho so rut gon de tra ve khi xem user khac (khong lo thong tin nhay cam). */
export interface PublicUser {
  uid: string;
  fullName: string;
  avatar?: string;
  personalStreak: number;
}
