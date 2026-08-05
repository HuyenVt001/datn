import { Module } from '@nestjs/common';
import { AstriteModule } from '../astrite/astrite.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { UsersModule } from '../users/users.module';
import { PayosService } from './payos.service';
import { TopupController } from './topup.controller';
import { TopupRepository } from './topup.repository';
import { TopupService } from './topup.service';

/**
 * Nap Astrite bang tien that qua PayOS (G6 — GACHA_PLAN.md muc 4).
 * - AstriteModule: cong Astrite + ghi so cai TRONG CUNG transaction voi don.
 * - AuthModule   : AdminJwtGuard (JwtModule) cho cac endpoint admin.
 * - UsersModule  : enrich uid -> ten o trang lich su nap cua admin.
 * - AuditModule  : ghi nhat ky moi thao tac admin (nhu cac module khac).
 */
@Module({
  imports: [AstriteModule, AuditModule, AuthModule, UsersModule],
  controllers: [TopupController],
  providers: [TopupService, TopupRepository, PayosService],
  exports: [TopupService, TopupRepository],
})
export class TopupModule {}
