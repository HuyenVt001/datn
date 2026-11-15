import { forwardRef, Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { ShopModule } from '../shop/shop.module';
import { UsersModule } from '../users/users.module';
import { FramesController } from './frames.controller';
import { FramesRepository } from './frames.repository';
import { FramesService } from './frames.service';

// AuthModule: AdminJwtGuard (JwtModule) cho endpoint admin. UsersModule: unlockedFrames.
// AuditModule: ghi nhat ky hanh dong admin (CRUD/grant khung).
// ShopModule (forwardRef — 2 module tham chieu vong): tu dong niem yet / go
// mat hang Shop khi khung doi dieu kien mo khoa SHOP <-> khac.
@Module({
  imports: [AuthModule, UsersModule, AuditModule, forwardRef(() => ShopModule)],
  controllers: [FramesController],
  providers: [FramesService, FramesRepository],
  exports: [FramesService, FramesRepository],
})
export class FramesModule {}
