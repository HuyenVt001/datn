/**
 * Cac dieu kien mo khoa khung (cap nhat 2026-11-15 — SHOP_PLAN.md):
 * - SHOP:             mua bang Astrite trong Cua hang. THAY `GACHA` (2026-08-05) va `QUEST_RANDOM` cu.
 * - STREAK_MILESTONE: dat moc streak ca nhan (unlockValue ∈ 3/7/14/30).
 * - POST_COUNT:       dang du unlockValue bai (tong so moment cua user).
 * - FRIEND_COUNT:     dat unlockValue ban be (1..MAX_FRIENDS).
 * - COOP_FIRST:       hoan thanh chup chung (co-op capture) lan dau — mo cho ca 2 nguoi.
 * - DEFAULT:          mo san cho moi user, khong can dieu kien.
 *
 * `QUEST_RANDOM` va `GACHA` DA BO. Doc Firestore cu con 2 gia tri do ->
 * FramesRepository.toEntity map sang `SHOP` (khong can migration; script
 * `seed:shop` chuan hoa lai du lieu cho sach).
 */
export const UNLOCK_TYPES = [
  'SHOP',
  'STREAK_MILESTONE',
  'POST_COUNT',
  'FRIEND_COUNT',
  'COOP_FIRST',
  'DEFAULT',
] as const;

export type UnlockType = (typeof UNLOCK_TYPES)[number];

/** Thuc the Frame (khung anh phan thuong) — admin quan ly catalog. */
export interface Frame {
  frameId: string;
  frameName: string;
  /** URL anh khung (upload qua /upload hoac admin dan URL). */
  imageUrl?: string;
  /** Dieu kien mo khoa. Doc cu (truoc 2026-07-26) khong co field nay -> repo suy tu milestone. */
  unlockType: UnlockType;
  /**
   * Nguong N cua dieu kien: STREAK_MILESTONE (3/7/14/30), POST_COUNT (>=1),
   * FRIEND_COUNT (1..20). Cac loai con lai = null (khong co nguong).
   */
  unlockValue?: number | null;
  /**
   * Legacy — app Android hien dang doc field nay de hien nhan "moc streak 🔥".
   * Repo/service luon giu dong bo: = unlockValue khi STREAK_MILESTONE, nguoc lai null.
   */
  milestone?: number | null;
  createdAt: string;
}

/** Frame kem trang thai da mo khoa cua user hien tai. */
export interface FrameWithUnlock extends Frame {
  isUnlocked: boolean;
}

/** 1 user dang so huu khung (cho trang admin xem danh sach so huu). */
export interface FrameOwner {
  uid: string;
  email?: string;
  fullName: string;
  avatar?: string;
}
