import { forwardRef, Module } from '@nestjs/common';
import { AstriteModule } from '../astrite/astrite.module';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { FramesModule } from '../frames/frames.module';
import { UsersModule } from '../users/users.module';
import { ShopController } from './shop.controller';
import { ShopRepository } from './shop.repository';
import { ShopService } from './shop.service';

/**
 * Cua hang (2026-11-15 — SHOP_PLAN.md, thay GachaModule).
 * - AstriteModule: ghi so du + so cai trong CUNG transaction mua hang.
 * - AuthModule   : AdminJwtGuard (JwtModule) cho endpoint admin.
 * - FramesModule : kiem tra khung SHOP khi niem yet / mua. forwardRef vi
 *   FramesService dung nguoc ShopService de tu dong niem yet khung SHOP.
 * - UsersModule  : tang vat pham, danh sach so huu, enrich ten nguoi mua.
 * - AuditModule  : ghi nhat ky moi thao tac admin.
 */
@Module({
  imports: [AstriteModule, AuditModule, AuthModule, forwardRef(() => FramesModule), UsersModule],
  controllers: [ShopController],
  providers: [ShopService, ShopRepository],
  exports: [ShopService],
})
export class ShopModule {}
