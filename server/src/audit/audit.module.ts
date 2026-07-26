import { Module } from '@nestjs/common';
import { AuditRepository } from './audit.repository';
import { AuditService } from './audit.service';

/**
 * Audit log dung chung: AdminModule (khoa user, cap/thu quyen, xoa bai) va
 * FramesModule (CRUD/grant khung) deu import. Chi phu thuoc FirebaseModule
 * (@Global) nen khong tao vong lap module.
 */
@Module({
  providers: [AuditService, AuditRepository],
  exports: [AuditService],
})
export class AuditModule {}
