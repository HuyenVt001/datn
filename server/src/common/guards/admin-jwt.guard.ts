import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';

/**
 * Guard cho luong ADMIN (React): verify JWT do server tu phat (khong phai Firebase token).
 * Payload JWT chua { sub: uid, email, role: 'admin' }.
 */
@Injectable()
export class AdminJwtGuard implements CanActivate {
  constructor(
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<Request>();
    const [type, token] = request.headers.authorization?.split(' ') ?? [];
    if (type !== 'Bearer' || !token) {
      throw new UnauthorizedException('Thieu token admin.');
    }

    try {
      const payload = await this.jwt.verifyAsync(token, {
        secret: this.config.get<string>('JWT_SECRET'),
      });
      (request as Request & { user: unknown }).user = {
        uid: payload.sub,
        email: payload.email,
        role: payload.role,
        admin: payload.role === 'admin',
      };
      return true;
    } catch {
      throw new UnauthorizedException('Token admin khong hop le hoac da het han.');
    }
  }
}
