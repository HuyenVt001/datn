import type { AdminLoginResult } from '../types';
import { post } from './client';

/** Doi Firebase ID token lay JWT admin cua server. */
export function adminLogin(idToken: string): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/auth/admin/login', { idToken });
}
