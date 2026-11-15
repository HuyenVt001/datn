import { ItemType } from './shop-item.entity';

/**
 * 1 lan mua o Shop (collection `shopPurchases`, THAY `gachaRolls`).
 * Snapshot ten/loai/gia luc mua -> lich su khong vo khi admin sua/xoa mat hang.
 */
export interface ShopPurchase {
  purchaseId: string;
  uid: string;
  itemId: string;
  itemName: string;
  itemType: ItemType;
  refId: string;
  /** Astrite da tra. */
  price: number;
  /** So du sau khi tru. */
  balanceAfter: number;
  createdAt: string;
}

/** Ket qua tra ve cho app sau khi mua. */
export interface PurchaseOutcome {
  purchase: ShopPurchase;
  astriteAfter: number;
}
