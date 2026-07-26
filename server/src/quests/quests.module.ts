import { Module } from '@nestjs/common';
import { FramesModule } from '../frames/frames.module';
import { UsersModule } from '../users/users.module';
import { QuestsController } from './quests.controller';
import { QuestsRepository } from './quests.repository';
import { QuestsService } from './quests.service';

// UsersModule: doc unlockedFrames de chon khung thuong. FramesModule: unlockForUser + catalog.
@Module({
  imports: [UsersModule, FramesModule],
  controllers: [QuestsController],
  providers: [QuestsService, QuestsRepository],
  exports: [QuestsService, QuestsRepository],
})
export class QuestsModule {}
