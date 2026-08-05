import type { AdminTopupResult, TopupOrderStatus, TopupPackage } from '../types';
import { del, get, patch, post } from './client';

/** Body them goi nap. `priceVnd` toi thieu 1.000d (gioi han cua PayOS). */
export interface CreateTopupPackageBody {
  name: string;
  astrite: number;
  priceVnd: number;
  isActive?: boolean;
  isTest?: boolean;
  sortOrder?: number;
}

/** Body sua goi nap — moi field deu tuy chon. */
export type UpdateTopupPackageBody = Partial<CreateTopupPackageBody>;

/** Bo loc trang lich su nap. */
export interface TopupFilter {
  uid?: string;
  status?: TopupOrderStatus;
  /** YYYY-MM-DD (UTC). */
  date?: string;
  limit?: number;
}

/** Toan bo goi nap, ke ca goi dang tat. */
export function listTopupPackages(): Promise<TopupPackage[]> {
  return get<TopupPackage[]>('/topup/packages/admin');
}

export function createTopupPackage(body: CreateTopupPackageBody): Promise<TopupPackage> {
  return post<TopupPackage>('/topup/packages', body);
}

export function updateTopupPackage(
  packageId: string,
  body: UpdateTopupPackageBody,
): Promise<TopupPackage> {
  return patch<TopupPackage>(`/topup/packages/${packageId}`, body);
}

export function deleteTopupPackage(packageId: string) {
  return del<{ packageId: string }>(`/topup/packages/${packageId}`);
}

/** Lich su nap toan he thong + thong ke doanh thu (loc theo user / trang thai / ngay). */
export function listTopupOrders(filter: TopupFilter = {}): Promise<AdminTopupResult> {
  return get<AdminTopupResult>('/topup/history/admin', filter as Record<string, unknown>);
}
