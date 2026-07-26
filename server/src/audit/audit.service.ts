import { Injectable, Logger } from '@nestjs/common';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { PaginatedResult, PaginationDto } from '../common/dto/pagination.dto';
import { AuditRepository } from './audit.repository';
import { AdminAction, AdminLog } from './entities/admin-log.entity';

/**
 * Audit log: ghi lai MOI hanh dong cua admin (khoa user, cap/thu quyen, sua
 * khung, xoa bai...). Ghi best-effort — loi log KHONG lam fail thao tac chinh.
 */
@Injectable()
export class AuditService {
  private readonly logger = new Logger(AuditService.name);

  constructor(private readonly repo: AuditRepository) {}

  /** Ghi 1 dong nhat ky (fire-and-forget an toan — khong bao gio throw). */
  async log(
    actor: AuthUser,
    action: AdminAction,
    target?: { id?: string; label?: string },
  ): Promise<void> {
    try {
      await this.repo.add({
        actorUid: actor.uid,
        actorEmail: actor.email,
        action,
        targetId: target?.id,
        targetLabel: target?.label,
        createdAt: new Date().toISOString(),
      });
    } catch (e) {
      this.logger.warn(`Khong ghi duoc audit log ${action}: ${(e as Error).message}`);
    }
  }

  /** Danh sach nhat ky cho trang admin (moi nhat truoc, phan trang trong bo nho). */
  async list(pagination: PaginationDto): Promise<PaginatedResult<AdminLog>> {
    const all = await this.repo.listLatest();
    const { page, limit } = pagination;
    const start = (page - 1) * limit;
    return { items: all.slice(start, start + limit), page, limit, total: all.length };
  }
}
