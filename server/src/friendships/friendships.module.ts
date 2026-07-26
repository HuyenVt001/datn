import { Module } from '@nestjs/common';
import { FramesModule } from '../frames/frames.module';
import { UsersModule } from '../users/users.module';
import { FriendshipsController } from './friendships.controller';
import { FriendshipsRepository } from './friendships.repository';
import { FriendshipsService } from './friendships.service';

// UsersModule: UsersService (invite code, public profile).
// FramesModule: mo khung dieu kien FRIEND_COUNT khi ket ban thanh cong.
@Module({
  imports: [UsersModule, FramesModule],
  controllers: [FriendshipsController],
  providers: [FriendshipsService, FriendshipsRepository],
  exports: [FriendshipsService, FriendshipsRepository],
})
export class FriendshipsModule {}
