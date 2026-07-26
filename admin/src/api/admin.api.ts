import type { AdminStats, AdminUser, Paginated } from '../types';
import { get, patch, post } from './client';

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
