import type {
  AdminLog,
  AdminMoment,
  AdminStats,
  AdminUser,
  AiVerification,
  AiVerificationOutcome,
  DailyStat,
  Paginated,
} from '../types';
import { del, get, patch, post } from './client';

/** Danh sach nguoi dung, tim kiem theo email/ten + phan trang. */
export function listUsers(params: {
  search?: string;
  page?: number;
  limit?: number;
}): Promise<Paginated<AdminUser>> {
  return get<Paginated<AdminUser>>('/admin/users', params);
}

/** Thong ke tong quan cho dashboard. */
export function getStats(): Promise<AdminStats> {
  return get<AdminStats>('/admin/stats');
}

/** Khoa (disabled=true) / mo khoa (false) tai khoan. */
export function setUserDisabled(uid: string, disabled: boolean) {
  return patch<{ uid: string; disabled: boolean }>(`/admin/users/${uid}/disabled`, { disabled });
}

/** Cap quyen admin (custom claim) cho user co san — user do phai dang nhap lai moi co hieu luc. */
export function grantAdmin(uid: string) {
  return post<{ uid: string; admin: boolean }>(`/admin/users/${uid}/grant-admin`);
}

/** Thu hoi quyen admin — hieu luc NGAY (guard server re-check moi request). */
export function revokeAdmin(uid: string) {
  return post<{ uid: string; admin: boolean }>(`/admin/users/${uid}/revoke-admin`);
}

/** Thong ke theo ngay (moment + user moi) cho bieu do dashboard. */
export function getDailyStats(days = 7): Promise<DailyStat[]> {
  return get<DailyStat[]>('/admin/stats/daily', { days });
}

/** Danh sach bai dang moi nhat (kiem duyet noi dung). */
export function listMoments(params: {
  page?: number;
  limit?: number;
}): Promise<Paginated<AdminMoment>> {
  return get<Paginated<AdminMoment>>('/admin/moments', params);
}

/** Xoa bai dang vi pham (kem views/reactions; server ghi audit log). */
export function deleteMoment(momentId: string) {
  return del<{ momentId: string }>(`/admin/moments/${momentId}`);
}

/** Nhat ky hanh dong admin (moi nhat truoc). */
export function listLogs(params: { page?: number; limit?: number }): Promise<Paginated<AdminLog>> {
  return get<Paginated<AdminLog>>('/admin/logs', params);
}

/** Log AI xac minh anh quest (moi nhat truoc) — loc outcome/date/uid, phan trang (2026-08-16). */
export function listAiVerifications(params: {
  page?: number;
  limit?: number;
  outcome?: AiVerificationOutcome;
  date?: string;
  uid?: string;
}): Promise<Paginated<AiVerification>> {
  return get<Paginated<AiVerification>>('/admin/ai-verifications', params);
}
