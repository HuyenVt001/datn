import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { Role } from '../constants';
import { AuthUser } from '../decorators/current-user.decorator';
import { ROLES_KEY } from '../decorators/roles.decorator';

/**
 * Kiem tra role sau khi da xac thuc. Dung sau AdminJwtGuard/FirebaseAuthGuard.
 * admin xac dinh qua Firebase custom claims { admin: true } hoac role trong JWT admin.
 */
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<Role[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!required || required.length === 0) {
      return true;
    }

    const request = context.switchToHttp().getRequest<Request & { user?: AuthUser }>();
    const user = request.user;
    const isAdmin = user?.admin === true || user?.role === 'admin';

    if (required.includes('admin') && !isAdmin) {
      throw new ForbiddenException('Ban khong co quyen truy cap chuc nang nay.');
    }
    return true;
  }
}
