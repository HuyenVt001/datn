import { Module } from '@nestjs/common';
import { QuestsController } from './quests.controller';
import { QuestsRepository } from './quests.repository';
import { QuestsService } from './quests.service';

@Module({
  controllers: [QuestsController],
  providers: [QuestsService, QuestsRepository],
  exports: [QuestsService, QuestsRepository],
})
export class QuestsModule {}
