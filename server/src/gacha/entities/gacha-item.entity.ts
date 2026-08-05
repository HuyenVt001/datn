/**
 * Vat pham quay ra tu gacha (2026-08-05 — GACHA_PLAN.md muc 2.2).
 *
 * Admin quan ly METADATA (ten, anh dai dien, bat/tat); con ASSET THAT cua skin
 * va hieu ung touch nam trong APK, khop qua `refId`. Admin KHONG tao duoc skin
 * / hieu ung moi — chi sua thong tin cua nhung id da co san trong app.
 */
export const ITEM_TYPES = ['FRAME', 'EFFECT', 'SKIN'] as const;
export type ItemType = (typeof ITEM_TYPES)[number];

/** Pham chat cua VAT PHAM. Bac N khong phai vat pham (chi tra Astrite). */
export const ITEM_RARITIES = ['R', 'SR', 'SSR'] as const;
export type ItemRarity = (typeof ITEM_RARITIES)[number];

/** Bac co the quay ra: N (Astrite) + 3 bac vat pham. */
export type RollTier = 'N' | ItemRarity;

/**
 * Anh xa mac dinh theo spec: khung anh = R, hieu ung = SR, skin = SSR.
 * Chi dung lam GOI Y khi admin tao vat pham; luc quay van loc theo `rarity`
 * that cua tung doc, nen sau nay doi anh xa cung khong vo thuat toan.
 */
export const DEFAULT_RARITY_BY_TYPE: Record<ItemType, ItemRarity> = {
  FRAME: 'R',
  EFFECT: 'SR',
  SKIN: 'SSR',
};

export interface GachaItem {
  itemId: string;
  itemName: string;
  itemType: ItemType;
  rarity: ItemRarity;
  /** Anh dai dien hien o the ket qua quay / trang admin / lich su. */
  imageUrl?: string;
  /**
   * Tro toi vat pham that:
   * - FRAME  -> frameId (string, doc trong collection `frames`)
   * - SKIN   -> skinId (so, khop SkinRegistry trong app)
   * - EFFECT -> effectId (so, khop TouchEffectRegistry trong app)
   */
  refId: string;
  /** Tat vat pham -> khong quay ra nua (van giu cho ai da so huu). */
  isActive: boolean;
  sortOrder: number;
  createdAt: string;
}
