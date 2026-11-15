/**
 * So cai tien te Astrite (2026-08-05; cap nhat 2026-11-15 — SHOP_PLAN.md).
 * MOI thay doi so du `users.astrite` phai ghi kem 1 dong o day, khong ngoai le.
 * Doi chieu: tong `amount` cua 1 uid == `users.astrite` cua uid do.
 */
export const ASTRITE_TX_TYPES = [
  'SIGNUP_BONUS', // +1600 tang 1 lan khi tao tai khoan
  'QUEST_REWARD', // +60 khi xong 2/2 daily quest co dinh
  'AI_QUEST_REWARD', // +30 khi AI xac minh anh khop quest AI cua ngay (2026-08-15)
  'TOPUP', // + nap tien qua PayOS
  'SHOP_PURCHASE', // - tru khi mua vat pham o Shop (2026-11-15, thay GACHA_SPEND/GACHA_REFUND)
  'ADMIN_ADJUST', // +/- admin chinh tay (chua lam UI, chua sang truoc)
] as const;

export type AstriteTxType = (typeof ASTRITE_TX_TYPES)[number];

export interface AstriteTransaction {
  id: string;
  uid: string;
  type: AstriteTxType;
  /** Duong = cong, am = tru. */
  amount: number;
  /** So du SAU giao dich — de doi soat nhanh ma khong phai cong don ca so cai. */
  balanceAfter: number;
  /** Tham chieu nguon: purchaseId / orderCode / dateKey cua quest... */
  refId?: string;
  createdAt: string; // ISO
}

/** Payload tao moi (id + createdAt do repository sinh). */
export type NewAstriteTransaction = Omit<AstriteTransaction, 'id' | 'createdAt'>;
