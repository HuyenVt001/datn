import type { Frame, FrameOwnersResult, UnlockType } from '../types';
import { del, get, patch, post } from './client';

/** Body them/sua khung: dieu kien mo khoa + nguong N (bat buoc voi loai can nguong). */
export interface FrameBody {
  frameName?: string;
  imageUrl?: string;
  unlockType?: UnlockType;
  unlockValue?: number;
}

/** Toan bo catalog khung anh (route rieng cho admin). */
export function listFrames(): Promise<Frame[]> {
  return get<Frame[]>('/frames/admin');
}

/** Them khung moi (imageUrl lay tu uploadImage truoc do). */
export function createFrame(body: FrameBody & { frameName: string }): Promise<Frame> {
  return post<Frame>('/frames', body);
}

/** Sua ten / anh / dieu kien mo khoa cua khung. */
export function updateFrame(frameId: string, body: FrameBody): Promise<Frame> {
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

/** Danh sach user dang so huu khung. */
export function listFrameOwners(frameId: string): Promise<FrameOwnersResult> {
  return get<FrameOwnersResult>(`/frames/${frameId}/owners`);
}
