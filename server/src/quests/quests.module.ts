import { Module } from '@nestjs/common';
import { AstriteModule } from '../astrite/astrite.module';
import { FramesModule } from '../frames/frames.module';
import { QuestsController } from './quests.controller';
import { QuestsRepository } from './quests.repository';
import { QuestsService } from './quests.service';

// AstriteModule: cong 60 Astrite khi xong 2/2 quest.
// FramesModule: unlockForUser + catalog cho thuong moc streak.
@Module({
  imports: [AstriteModule, FramesModule],
  controllers: [QuestsController],
  providers: [QuestsService, QuestsRepository],
  exports: [QuestsService, QuestsRepository],
})
export class QuestsModule {}
