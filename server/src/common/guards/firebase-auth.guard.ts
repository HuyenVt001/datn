import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { FirebaseService } from '../../firebase/firebase.service';
import { IS_PUBLIC_KEY } from '../decorators/public.decorator';

/**
 * Guard cho luong APP: verify Firebase ID token trong header Authorization: Bearer <token>.
 * Endpoint gan @Public() se duoc bo qua.
 */
@Injectable()
export class FirebaseAuthGuard implements CanActivate {
  constructor(
    private readonly firebase: FirebaseService,
    private readonly reflector: Reflector,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) {
      return true;
    }

    const request = context.switchToHttp().getRequest<Request>();
    const token = this.extractToken(request);
    if (!token) {
      throw new UnauthorizedException('Thieu token xac thuc.');
    }

    try {
      const decoded = await this.firebase.auth().verifyIdToken(token);
      (request as Request & { user: unknown }).user = {
        ...decoded,
        uid: decoded.uid,
        email: decoded.email,
        admin: decoded.admin === true,
      };
      return true;
    } catch {
      throw new UnauthorizedException('Token khong hop le hoac da het han.');
    }
  }

  private extractToken(request: Request): string | undefined {
    const [type, token] = request.headers.authorization?.split(' ') ?? [];
    return type === 'Bearer' ? token : undefined;
  }
}
