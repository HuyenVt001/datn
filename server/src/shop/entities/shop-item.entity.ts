import {
  SHOP_DEFAULT_PRICE_EFFECT,
  SHOP_DEFAULT_PRICE_FRAME,
  SHOP_DEFAULT_PRICE_SKIN,
} from '../../common/constants';

/**
 * Mat hang ban trong Shop (2026-11-15 — SHOP_PLAN.md, THAY `gachaItems`).
 *
 * Admin quan ly METADATA + GIA (ten, anh, gia, bat/tat, thu tu). ASSET THAT cua
 * skin va hieu ung touch nam trong APK, khop qua `refId`: admin niem yet skin /
 * hieu ung theo id da co san trong app — id moi phai code vao app truoc.
 */
export const ITEM_TYPES = ['FRAME', 'SKIN', 'EFFECT'] as const;
export type ItemType = (typeof ITEM_TYPES)[number];

/** Gia goi y khi tao mat hang moi (admin sua tung mon sau). */
export const DEFAULT_PRICE_BY_TYPE: Record<ItemType, number> = {
  FRAME: SHOP_DEFAULT_PRICE_FRAME,
  SKIN: SHOP_DEFAULT_PRICE_SKIN,
  EFFECT: SHOP_DEFAULT_PRICE_EFFECT,
};

export interface ShopItem {
  itemId: string;
  itemName: string;
  itemType: ItemType;
  /**
   * Tro toi vat pham that (luon luu dang CHUOI):
   * - FRAME  -> frameId (doc trong collection `frames`, unlockType = SHOP)
   * - SKIN   -> skinId (so nguyen >= 1, khop SkinRegistry trong app)
   * - EFFECT -> effectId (so nguyen >= 1, khop TouchEffectRegistry trong app)
   * Cap (itemType, refId) la DUY NHAT — 1 vat pham chi niem yet 1 lan.
   */
  refId: string;
  /** Gia ban (Astrite, so nguyen >= 0). */
  price: number;
  /** Anh dai dien. Skin/hieu ung khong co anh -> app tu ve preview tu asset trong APK. */
  imageUrl?: string;
  /** Tat = an khoi Shop (ai da mua van giu). */
  isActive: boolean;
  sortOrder: number;
  createdAt: string;
}

/** Mat hang kem co da so huu cua user hien tai (catalog cho app). */
export interface ShopItemForUser extends ShopItem {
  isOwned: boolean;
}

/** 1 user dang so huu vat pham (drawer "Ai đang sở hữu?" o trang admin). */
export interface ShopItemOwner {
  uid: string;
  email?: string;
  fullName: string;
  avatar?: string;
}
