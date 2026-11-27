import type { AdminPurchasesResult, ShopItem, ShopItemOwnersResult, ShopItemType } from '../types';
import { del, get, patch, post } from './client';

/**
 * Body niem yet mat hang (POST /shop/items).
 * - FRAME: `refId` = frameId cua khung co dieu kien "Bán tại Cửa hàng".
 * - SKIN / EFFECT: `refId` = id so (>= 1) da co san trong APK.
 * Bo trong `price` -> server dung gia mac dinh theo loai (320 / 4800 / 1600).
 */
export interface CreateShopItemBody {
  itemName: string;
  itemType: ShopItemType;
  refId: string;
  price?: number;
  imageUrl?: string;
  isActive?: boolean;
  sortOrder?: number;
}

/** Body sua mat hang — KHONG doi duoc `itemType`/`refId` (server chan). */
export type UpdateShopItemBody = Partial<Omit<CreateShopItemBody, 'itemType' | 'refId'>>;

/** Bo loc trang lich su mua. */
export interface PurchaseFilter {
  uid?: string;
  itemType?: ShopItemType;
  /** YYYY-MM-DD (UTC). */
  date?: string;
  limit?: number;
}

/** Toan bo mat hang, ke ca mat hang dang tat. */
export function listShopItems(): Promise<ShopItem[]> {
  return get<ShopItem[]>('/shop/items/admin');
}

export function createShopItem(body: CreateShopItemBody): Promise<ShopItem> {
  return post<ShopItem>('/shop/items', body);
}

export function updateShopItem(itemId: string, body: UpdateShopItemBody): Promise<ShopItem> {
  return patch<ShopItem>(`/shop/items/${itemId}`, body);
}

export function deleteShopItem(itemId: string) {
  return del<{ itemId: string }>(`/shop/items/${itemId}`);
}

/** Tang vat pham cho user — idempotent, khong tru Astrite, khong ghi lich su mua. */
export function grantShopItem(itemId: string, uid: string) {
  return post<{ itemId: string; uid: string }>(`/shop/items/${itemId}/grant/${uid}`);
}

/** Danh sach user dang so huu 1 vat pham. */
export function listShopItemOwners(itemId: string): Promise<ShopItemOwnersResult> {
  return get<ShopItemOwnersResult>(`/shop/items/${itemId}/owners`);
}

/** Lich su mua toan he thong (loc theo user / loai / ngay). */
export function listShopPurchases(filter: PurchaseFilter = {}): Promise<AdminPurchasesResult> {
  return get<AdminPurchasesResult>('/shop/history/admin', filter as Record<string, unknown>);
}
