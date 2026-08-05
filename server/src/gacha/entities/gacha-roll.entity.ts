import { ItemType, RollTier } from './gacha-item.entity';

/** 1 ket qua le trong 1 lan quay (x10 -> 10 phan tu). */
export interface RollResultEntry {
  tier: RollTier;
  /** Bac N: so Astrite nhan duoc (1..60). Cac bac khac: undefined. */
  astriteAmount?: number;
  /** Bac R/SR/SSR: thong tin vat pham. */
  itemId?: string;
  itemName?: string;
  itemType?: ItemType;
  refId?: string;
  imageUrl?: string;
  /** True = da so huu tu truoc -> khong mo khoa gi, chi hoan Astrite. */
  isDuplicate: boolean;
  /** Astrite hoan lai (bac N dung `astriteAmount`, khong dung field nay). */
  refundAstrite: number;
}

export const ROLL_TYPES = ['SINGLE', 'TEN'] as const;
export type RollType = (typeof ROLL_TYPES)[number];

/** 1 doc = 1 lan bam nut quay (x10 van la 1 doc, 10 phan tu trong `results`). */
export interface GachaRoll {
  rollId: string;
  uid: string;
  rollType: RollType;
  /** Astrite da tru. */
  cost: number;
  results: RollResultEntry[];
  /** Tong Astrite thu ve (bac N + hoan trung). */
  refundTotal: number;
  /** So du sau khi tru chi phi va cong lai refund. */
  balanceAfter: number;
  createdAt: string;
}

/** Ket qua tra ve cho app sau 1 lan quay. */
export interface RollOutcome {
  rollId: string;
  rollType: RollType;
  cost: number;
  results: RollResultEntry[];
  refundTotal: number;
  astriteAfter: number;
}
