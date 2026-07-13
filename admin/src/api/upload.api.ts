import type { ApiEnvelope, UploadResult } from '../types';
import { client } from './client';

/** Upload anh (khung anh...) qua route admin, tra ve URL Cloudinary. */
export async function uploadImage(file: File): Promise<UploadResult> {
  const form = new FormData();
  form.append('file', file);
  const res = await client.post<ApiEnvelope<UploadResult>>('/upload/admin', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data.data;
}
