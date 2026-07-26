import { Module } from '@nestjs/common';
import { AdminModule } from './admin/admin.module';
import { AppController } from './app.controller';
import { AuthModule } from './auth/auth.module';
import { ConfigModule } from './config/config.module';
import { FirebaseModule } from './firebase/firebase.module';
import { FramesModule } from './frames/frames.module';
import { FriendshipsModule } from './friendships/friendships.module';
import { MessagesModule } from './messages/messages.module';
import { MomentsModule } from './moments/moments.module';
import { QuestsModule } from './quests/quests.module';
import { UploadModule } from './upload/upload.module';
import { UsersModule } from './users/users.module';

@Module({
  imports: [
    // Ha tang
    ConfigModule,
    FirebaseModule,
    AuthModule,
    // Domain
    UploadModule,
    UsersModule,
    FriendshipsModule,
    MomentsModule,
    MessagesModule,
    FramesModule,
    QuestsModule,
    AdminModule,
  ],
  controllers: [AppController],
})
export class AppModule {}
