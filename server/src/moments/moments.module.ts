import { Module } from '@nestjs/common';
import { FriendshipsModule } from '../friendships/friendships.module';
import { UsersModule } from '../users/users.module';
import { MomentsController } from './moments.controller';
import { MomentsRepository } from './moments.repository';
import { MomentsService } from './moments.service';

// Import Users + Friendships de wire streak ca nhan/ban be va gui FCM cho ban be.
@Module({
  imports: [UsersModule, FriendshipsModule],
  controllers: [MomentsController],
  providers: [MomentsService, MomentsRepository],
  exports: [MomentsService, MomentsRepository],
})
export class MomentsModule {}
