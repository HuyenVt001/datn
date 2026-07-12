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
   * Upload buffer len Cloudinary (folder snapget/). resource_type auto: nhan ca anh & video.
   * Video dai qua MAX_VIDEO_SECONDS -> xoa asset vua len + bao loi.
   */
  async upload(file: Express.Multer.File): Promise<UploadResult> {
    if (!this.configured) {
      throw new BadRequestException('Server chua cau hinh Cloudinary.');
    }

    const res = await new Promise<UploadApiResponse>((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        { folder: 'snapget', resource_type: 'auto' },
        (err, result) => (err || !result ? reject(err) : resolve(result)),
      );
      stream.end(file.buffer);
    });

    const resourceType = res.resource_type === 'video' ? 'video' : 'image';

    // Enforce rule nghiep vu: video ngan toi da 5 giay.
    if (resourceType === 'video' && (res.duration ?? 0) > MAX_VIDEO_SECONDS) {
      await cloudinary.uploader
        .destroy(res.public_id, { resource_type: 'video' })
        .catch(() => this.logger.warn(`Khong xoa duoc asset qua dai: ${res.public_id}`));
      throw new BadRequestException(`Video toi da ${MAX_VIDEO_SECONDS} giay.`);
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
