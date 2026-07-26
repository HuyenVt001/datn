import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';

/**
 * Xac thuc: phat JWT cho luong admin. FirebaseService da @Global nen khong can import.
 * JwtModule export de AdminJwtGuard (dung o cac module admin) verify duoc token.
 */
@Module({
  imports: [
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get<string>('JWT_SECRET'),
        signOptions: { expiresIn: config.get<string>('JWT_EXPIRES_IN') ?? '1d' },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService, AdminJwtGuard],
  exports: [JwtModule, AuthService],
})
export class AuthModule {}
