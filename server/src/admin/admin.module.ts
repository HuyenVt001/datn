import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { GachaModule } from '../gacha/gacha.module';
import { MomentsModule } from '../moments/moments.module';
import { QuestsModule } from '../quests/quests.module';
import { AdminController } from './admin.controller';
import { AdminRepository } from './admin.repository';
import { AdminService } from './admin.service';

// AuthModule (JwtModule): AdminJwtGuard verify JWT admin. QuestsModule: thong ke quest hom nay.
// MomentsModule: kiem duyet bai dang (list/xoa). AuditModule: nhat ky hanh dong admin.
// GachaModule: so luot quay o o thong ke dashboard.
@Module({
  imports: [AuthModule, QuestsModule, MomentsModule, AuditModule, GachaModule],
  controllers: [AdminController],
  providers: [AdminService, AdminRepository],
})
export class AdminModule {}
