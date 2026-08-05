import type {
  AdminRoll,
  GachaItem,
  GachaItemOwnersResult,
  ItemRarity,
  ItemType,
  RollTier,
} from '../types';
import { del, get, patch, post } from './client';

/** Body them vat pham. Server CHI cho tao `itemType = FRAME`. */
export interface CreateGachaItemBody {
  itemName: string;
  itemType: ItemType;
  rarity: ItemRarity;
  refId: string;
  imageUrl?: string;
  isActive?: boolean;
  sortOrder?: number;
}

/** Body sua vat pham — KHONG doi duoc `itemType`/`refId` (server chan). */
export interface UpdateGachaItemBody {
  itemName?: string;
  rarity?: ItemRarity;
  imageUrl?: string;
  isActive?: boolean;
  sortOrder?: number;
}

/** Bo loc trang lich su quay. */
export interface RollFilter {
  uid?: string;
  tier?: RollTier;
  /** YYYY-MM-DD (UTC). */
  date?: string;
  limit?: number;
}

/** Toan bo kho vat pham, ke ca vat pham dang tat. */
export function listGachaItems(): Promise<GachaItem[]> {
  return get<GachaItem[]>('/gacha/items/admin');
}

export function createGachaItem(body: CreateGachaItemBody): Promise<GachaItem> {
  return post<GachaItem>('/gacha/items', body);
}

export function updateGachaItem(itemId: string, body: UpdateGachaItemBody): Promise<GachaItem> {
  return patch<GachaItem>(`/gacha/items/${itemId}`, body);
}

export function deleteGachaItem(itemId: string) {
  return del<{ itemId: string }>(`/gacha/items/${itemId}`);
}

/** Tang vat pham cho user (kho thuong) — idempotent, khong lien quan Astrite. */
export function grantGachaItem(itemId: string, uid: string) {
  return post<{ itemId: string; uid: string }>(`/gacha/items/${itemId}/grant/${uid}`);
}

/** Danh sach user dang so huu 1 vat pham. */
export function listGachaItemOwners(itemId: string): Promise<GachaItemOwnersResult> {
  return get<GachaItemOwnersResult>(`/gacha/items/${itemId}/owners`);
}

/** Lich su quay toan he thong (loc theo user / bac / ngay). */
export function listGachaRolls(filter: RollFilter = {}): Promise<AdminRoll[]> {
  return get<AdminRoll[]>('/gacha/history/admin', filter as Record<string, unknown>);
}
