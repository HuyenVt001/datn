import { Module } from '@nestjs/common';
import { FriendshipsModule } from '../friendships/friendships.module';
import { QuestsModule } from '../quests/quests.module';
import { UploadModule } from '../upload/upload.module';
import { UsersModule } from '../users/users.module';
import { CoopController } from './coop.controller';
import { CoopRepository } from './coop.repository';
import { CoopService } from './coop.service';
import { MomentsController } from './moments.controller';
import { MomentsRepository } from './moments.repository';
import { MomentsService } from './moments.service';

// Users + Friendships: streak ca nhan/ban be + FCM. Quests: quest POST_MOMENT khi dang bai.
// Upload: uploadBuffer cho anh chup chung da ghep (sharp).
// LUU Y: CoopController dat TRUOC MomentsController de route 'moments/coop' khong bi nuot boi ':id'.
@Module({
  imports: [UsersModule, FriendshipsModule, QuestsModule, UploadModule],
  controllers: [CoopController, MomentsController],
  providers: [MomentsService, MomentsRepository, CoopService, CoopRepository],
  exports: [MomentsService, MomentsRepository],
})
export class MomentsModule {}
