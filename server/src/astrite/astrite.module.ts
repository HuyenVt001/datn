import { Module } from '@nestjs/common';
import { AstriteRepository } from './astrite.repository';
import { AstriteService } from './astrite.service';

/**
 * Vi tien te Astrite (2026-08-05). Chua co controller rieng: so du tra kem
 * trong `GET /users/me`, lich su quay/nap co endpoint rieng o module gacha/topup.
 * Cac module khac (users, quests, gacha, topup) import module nay de cong/tru.
 */
@Module({
  providers: [AstriteService, AstriteRepository],
  exports: [AstriteService, AstriteRepository],
})
export class AstriteModule {}
