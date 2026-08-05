/**
 * So cai tien te Astrite (2026-08-05 — GACHA_PLAN.md muc 2.2).
 * MOI thay doi so du `users.astrite` phai ghi kem 1 dong o day, khong ngoai le.
 * Doi chieu: tong `amount` cua 1 uid == `users.astrite` cua uid do.
 */
export const ASTRITE_TX_TYPES = [
  'SIGNUP_BONUS', // +1600 tang 1 lan khi tao tai khoan
  'QUEST_REWARD', // +60 khi xong 2/2 daily quest
  'TOPUP', // + nap tien qua PayOS
  'GACHA_SPEND', // - tru khi quay (160 hoac 1440)
  'GACHA_REFUND', // + hoan lai khi quay trung vat pham da so huu / ra bac N
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
  /** Tham chieu nguon: rollId / orderCode / dateKey cua quest... */
  refId?: string;
  createdAt: string; // ISO
}

/** Payload tao moi (id + createdAt do repository sinh). */
export type NewAstriteTransaction = Omit<AstriteTransaction, 'id' | 'createdAt'>;
