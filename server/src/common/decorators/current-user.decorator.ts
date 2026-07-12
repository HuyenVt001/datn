import { createParamDecorator, ExecutionContext } from '@nestjs/common';

/** Thong tin user gan vao request sau khi guard verify token. */
export interface AuthUser {
  uid: string;
  email?: string;
  admin?: boolean;
  [key: string]: unknown;
}

/** Lay user hien tai trong controller: `@CurrentUser() user: AuthUser`. */
export const CurrentUser = createParamDecorator(
  (data: keyof AuthUser | undefined, ctx: ExecutionContext): AuthUser | unknown => {
    const request = ctx.switchToHttp().getRequest<{ user: AuthUser }>();
    const user = request.user;
    return data ? user?.[data] : user;
  },
);
