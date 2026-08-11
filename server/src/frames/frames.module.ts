import { forwardRef, Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { GachaModule } from '../gacha/gacha.module';
import { UsersModule } from '../users/users.module';
import { FramesController } from './frames.controller';
import { FramesRepository } from './frames.repository';
import { FramesService } from './frames.service';

// AuthModule: AdminJwtGuard (JwtModule) cho endpoint admin. UsersModule: unlockedFrames.
// AuditModule: ghi nhat ky hanh dong admin (CRUD/grant khung).
// GachaModule (forwardRef — 2 module tham chieu vong): dong bo kho gacha khi
// khung doi dieu kien mo khoa GACHA <-> khac.
@Module({
  imports: [AuthModule, UsersModule, AuditModule, forwardRef(() => GachaModule)],
  controllers: [FramesController],
  providers: [FramesService, FramesRepository],
  exports: [FramesService, FramesRepository],
})
export class FramesModule {}
