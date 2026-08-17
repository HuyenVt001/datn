import {
  CanActivate,
  ExecutionContext,
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { timingSafeEqual } from 'crypto';
import { Request } from 'express';

/** Ten header ma cron-job.org gui kem (cau hinh trong dashboard cua cron). */
export const CRON_SECRET_HEADER = 'x-cron-secret';

/**
 * Guard cho endpoint do CRON goi (khong co user Firebase, khong phai admin):
 * so header `x-cron-secret` voi env `CRON_SECRET` bang timingSafeEqual.
 * - Thieu CRON_SECRET trong env -> 503 (tinh nang cron chua bat), khong bao gio
 *   cho qua khi chua cau hinh.
 * - Sai/thieu header -> 401.
 * Dung kem @Public() de FirebaseAuthGuard bo qua route.
 */
@Injectable()
export class CronSecretGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const expected = this.config.get<string>('CRON_SECRET')?.trim() ?? '';
    if (!expected) {
      throw new ServiceUnavailableException('Server chưa cấu hình CRON_SECRET.');
    }
    const req = context.switchToHttp().getRequest<Request>();
    const raw = req.headers[CRON_SECRET_HEADER];
    const provided = (Array.isArray(raw) ? raw[0] : raw) ?? '';
    const a = Buffer.from(provided);
    const b = Buffer.from(expected);
    if (a.length !== b.length || !timingSafeEqual(a, b)) {
      throw new UnauthorizedException('Sai cron secret.');
    }
    return true;
  }
}
