import { BadRequestException, Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { UploadApiResponse, v2 as cloudinary } from 'cloudinary';
import { MAX_VIDEO_SECONDS } from '../common/constants';

/** Ket qua upload tra ve cho client. */
export interface UploadResult {
  url: string;
  publicId: string;
  resourceType: 'image' | 'video';
  /** Do dai video (giay) — undefined voi anh. */
  duration?: number;
  width?: number;
  height?: number;
}

/**
 * Upload media len Cloudinary. App KHONG duoc upload thang — moi thu di qua day
 * de giau API secret va enforce rule video <= 5s (doc tu metadata Cloudinary).
 */
@Injectable()
export class UploadService implements OnModuleInit {
  private readonly logger = new Logger(UploadService.name);
  private configured = false;

  constructor(private readonly config: ConfigService) {}

  onModuleInit(): void {
    const cloudName = this.config.get<string>('CLOUDINARY_CLOUD_NAME');
    const apiKey = this.config.get<string>('CLOUDINARY_API_KEY');
    const apiSecret = this.config.get<string>('CLOUDINARY_API_SECRET');

    if (!cloudName || !apiKey || !apiSecret) {
      this.logger.warn('Thieu config Cloudinary trong .env — endpoint upload se bao loi.');
      return;
    }
    cloudinary.config({ cloud_name: cloudName, api_key: apiKey, api_secret: apiSecret });
    this.configured = true;
    this.logger.log('Cloudinary da cau hinh xong.');
  }

  /**
   * Upload buffer len Cloudinary (folder snapget/). resource_type auto: nhan anh, video, ghi am.
   * Video dai qua MAX_VIDEO_SECONDS -> xoa asset vua len + bao loi.
   */
  async upload(file: Express.Multer.File): Promise<UploadResult> {
    return this.uploadBuffer(file.buffer, 'snapget');
  }

  /** Upload buffer bat ky (vd: anh chup chung da ghep bang sharp) len Cloudinary. */
  async uploadBuffer(buffer: Buffer, folder = 'snapget'): Promise<UploadResult> {
    if (!this.configured) {
      throw new BadRequestException('Server chua cau hinh Cloudinary.');
    }

    const res = await new Promise<UploadApiResponse>((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        { folder, resource_type: 'auto' },
        (err, result) => (err || !result ? reject(err) : resolve(result)),
      );
      stream.end(buffer);
    });

    const resourceType = res.resource_type === 'video' ? 'video' : 'image';

    // Ghi am (VOICE) bi Cloudinary xep vao resource_type 'video' nhung khong bi
    // gioi han do dai. QUAN TRONG: doc is_audio tu KET QUA Cloudinary (phan loai
    // theo bytes that) — KHONG tin mimetype client gui len, vi user co the gia
    // Content-Type audio/* de lach luat "anh GIF" <= 3s.
    const isAudio = Boolean((res as UploadApiResponse & { is_audio?: boolean }).is_audio);

    // Enforce rule nghiep vu: "anh GIF" (clip ngan) toi da 3 giay (chot 2026-08-03).
    // +0.5s dung sai: app tu dong dung o moc 3s nhung timer + finalize lam clip
    // dai ~3.1-3.3s — khong co dung sai thi clip quay du 3s KHONG dang duoc.
    if (!isAudio && resourceType === 'video' && (res.duration ?? 0) > MAX_VIDEO_SECONDS + 0.5) {
      await cloudinary.uploader
        .destroy(res.public_id, { resource_type: 'video' })
        .catch(() => this.logger.warn(`Khong xoa duoc asset qua dai: ${res.public_id}`));
      throw new BadRequestException(`Anh GIF toi da ${MAX_VIDEO_SECONDS} giay.`);
    }

    return {
      url: res.secure_url,
      publicId: res.public_id,
      resourceType,
      duration: res.duration,
      width: res.width,
      height: res.height,
    };
  }
}
