import type { Frame } from '../types';
import { del, get, patch, post } from './client';

/** Toan bo catalog khung anh (route rieng cho admin). */
export function listFrames(): Promise<Frame[]> {
  return get<Frame[]>('/frames/admin');
}

/** Them khung moi (imageUrl lay tu uploadImage truoc do). */
export function createFrame(body: {
  frameName: string;
  imageUrl?: string;
  milestone?: number;
}): Promise<Frame> {
  return post<Frame>('/frames', body);
}

/** Sua ten / anh / moc streak cua khung (milestone: null = XOA moc). */
export function updateFrame(
  frameId: string,
  body: { frameName?: string; imageUrl?: string; milestone?: number | null },
): Promise<Frame> {
  return patch<Frame>(`/frames/${frameId}`, body);
}

/** Xoa khung khoi catalog. */
export function deleteFrame(frameId: string) {
  return del<{ frameId: string }>(`/frames/${frameId}`);
}

/** Cap (mo khoa) khung cho 1 user cu the. */
export function grantFrame(frameId: string, uid: string) {
  return post<{ frameId: string; uid: string }>(`/frames/${frameId}/grant/${uid}`);
}
