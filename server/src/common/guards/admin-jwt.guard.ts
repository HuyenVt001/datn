import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';
import { FirebaseService } from '../../firebase/firebase.service';

/**
 * Guard cho luong ADMIN (React): verify JWT do server tu phat (khong phai Firebase token).
 * Payload JWT chua { sub: uid, email, role: 'admin' }.
 *
 * Sau khi verify JWT, guard KIEM TRA LAI trang thai hien tai tren Firebase Auth
 * (con claim admin? co bi khoa?) — admin bi thu quyen / bi khoa thi phien admin
 * mat hieu luc NGAY, khong phai doi JWT het han (1 ngay).
 */
@Injectable()
export class AdminJwtGuard implements CanActivate {
  constructor(
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
    private readonly firebase: FirebaseService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<Request>();
    const [type, token] = request.headers.authorization?.split(' ') ?? [];
    if (type !== 'Bearer' || !token) {
      throw new UnauthorizedException('Thieu token admin.');
    }

    let payload;
    try {
      payload = await this.jwt.verifyAsync(token, {
        secret: this.config.get<string>('JWT_SECRET'),
      });
    } catch {
      throw new UnauthorizedException('Token admin khong hop le hoac da het han.');
    }

    // Re-check quyen hien hanh — khong tin claim "role" nam trong JWT da phat.
    let userRecord;
    try {
      userRecord = await this.firebase.auth().getUser(payload.sub);
    } catch {
      throw new UnauthorizedException('Tai khoan admin khong con ton tai.');
    }
    if (userRecord.customClaims?.admin !== true) {
      throw new UnauthorizedException('Quyen admin cua ban da bi thu hoi.');
    }
    if (userRecord.disabled) {
      throw new UnauthorizedException('Tai khoan cua ban da bi khoa.');
    }

    (request as Request & { user: unknown }).user = {
      uid: payload.sub,
      email: payload.email,
      role: payload.role,
      admin: true,
    };
    return true;
  }
}
