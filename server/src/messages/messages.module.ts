import { Module } from '@nestjs/common';
import { FriendshipsModule } from '../friendships/friendships.module';
import { UsersModule } from '../users/users.module';
import { MessagesController } from './messages.controller';
import { MessagesRepository } from './messages.repository';
import { MessagesService } from './messages.service';

// Import Users + Friendships: kiem tra ban be, wire friend streak, lay fcmTokens.
@Module({
  imports: [UsersModule, FriendshipsModule],
  controllers: [MessagesController],
  providers: [MessagesService, MessagesRepository],
  exports: [MessagesService, MessagesRepository],
})
export class MessagesModule {}
