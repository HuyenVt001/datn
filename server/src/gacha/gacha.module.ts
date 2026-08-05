import { Module } from '@nestjs/common';
import { AstriteModule } from '../astrite/astrite.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { FramesModule } from '../frames/frames.module';
import { UsersModule } from '../users/users.module';
import { GachaController } from './gacha.controller';
import { GachaRepository } from './gacha.repository';
import { GachaService } from './gacha.service';

/**
 * He thong gacha (2026-08-05 — GACHA_PLAN.md).
 * - AstriteModule: ghi so cai trong CUNG transaction voi lan quay.
 * - AuthModule   : AdminJwtGuard (JwtModule) cho cac endpoint admin.
 * - FramesModule : kiem tra refId co that khi admin them khung vao kho.
 * - UsersModule  : enrich uid -> ten o trang lich su quay cua admin.
 * - AuditModule  : ghi nhat ky moi thao tac admin (nhu cac module khac).
 */
@Module({
  imports: [AstriteModule, AuditModule, AuthModule, FramesModule, UsersModule],
  controllers: [GachaController],
  providers: [GachaService, GachaRepository],
  exports: [GachaService, GachaRepository],
})
export class GachaModule {}
