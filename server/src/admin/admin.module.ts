import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { QuestsModule } from '../quests/quests.module';
import { AdminController } from './admin.controller';
import { AdminRepository } from './admin.repository';
import { AdminService } from './admin.service';

// AuthModule (JwtModule): AdminJwtGuard verify JWT admin. QuestsModule: thong ke quest hom nay.
@Module({
  imports: [AuthModule, QuestsModule],
  controllers: [AdminController],
  providers: [AdminService, AdminRepository],
})
export class AdminModule {}
