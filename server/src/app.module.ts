import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { AdminModule } from './admin/admin.module';
import { AppController } from './app.controller';
import { AstriteModule } from './astrite/astrite.module';
import { AuditModule } from './audit/audit.module';
import { AuthModule } from './auth/auth.module';
import { ConfigModule } from './config/config.module';
import { FirebaseModule } from './firebase/firebase.module';
import { FramesModule } from './frames/frames.module';
import { FriendshipsModule } from './friendships/friendships.module';
import { GachaModule } from './gacha/gacha.module';
import { MessagesModule } from './messages/messages.module';
import { MomentsModule } from './moments/moments.module';
import { QuestsModule } from './quests/quests.module';
import { TopupModule } from './topup/topup.module';
import { UploadModule } from './upload/upload.module';
import { UsersModule } from './users/users.module';

@Module({
  imports: [
    // Ha tang
    ConfigModule,
    FirebaseModule,
    AuthModule,
    AuditModule,
    // Rate limit toan cuc (2026-07-26): 120 request/60s moi IP — chong spam/abuse.
    // Endpoint nhay cam (login admin) siet chat hon bang @Throttle rieng.
    ThrottlerModule.forRoot([{ ttl: 60_000, limit: 120 }]),
    // Domain
    UploadModule,
    AstriteModule, // vi tien te Astrite — users/quests/gacha/topup deu dung
    UsersModule,
    FriendshipsModule,
    MomentsModule,
    MessagesModule,
    FramesModule,
    QuestsModule,
    GachaModule, // gacha + vat pham (skin/hieu ung/khung)
    TopupModule, // nap Astrite qua PayOS (tien that)
    AdminModule,
  ],
  controllers: [AppController],
  providers: [{ provide: APP_GUARD, useClass: ThrottlerGuard }],
})
export class AppModule {}
