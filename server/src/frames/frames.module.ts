import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { UsersModule } from '../users/users.module';
import { FramesController } from './frames.controller';
import { FramesRepository } from './frames.repository';
import { FramesService } from './frames.service';

// AuthModule: AdminJwtGuard (JwtModule) cho endpoint admin. UsersModule: unlockedFrames.
@Module({
  imports: [AuthModule, UsersModule],
  controllers: [FramesController],
  providers: [FramesService, FramesRepository],
  exports: [FramesService, FramesRepository],
})
export class FramesModule {}
