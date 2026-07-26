import axios, { AxiosError } from 'axios';
import type { ApiEnvelope } from '../types';

/** Key localStorage giu JWT admin — dung chung voi AuthContext. */
export const TOKEN_KEY = 'snapget_admin_token';
export const EMAIL_KEY = 'snapget_admin_email';

/**
 * Axios instance dung chung: tu gan Bearer JWT, boc envelope, xu ly 401.
 * Page/component KHONG dung axios truc tiep — di qua cac ham get/post/patch/del ben duoi.
 */
export const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (res) => res,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    // Phien het han / token sai -> xoa token va quay ve trang login
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(EMAIL_KEY);
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login');
      }
    }
    // message cua server da la tieng Viet than thien -> dung truc tiep
    const message = error.response?.data?.message || 'Không thể kết nối máy chủ.';
    return Promise.reject(new Error(message));
  },
);

// ==== Cac ham boc data tu envelope ====

export async function get<T>(url: string, params?: object): Promise<T> {
  const res = await client.get<ApiEnvelope<T>>(url, { params });
  return res.data.data;
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.post<ApiEnvelope<T>>(url, body);
  return res.data.data;
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.patch<ApiEnvelope<T>>(url, body);
  return res.data.data;
}

export async function del<T>(url: string): Promise<T> {
  const res = await client.delete<ApiEnvelope<T>>(url);
  return res.data.data;
}
