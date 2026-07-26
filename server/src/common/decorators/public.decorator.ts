import { SetMetadata } from '@nestjs/common';

export const IS_PUBLIC_KEY = 'isPublic';

/** Danh dau endpoint khong can dang nhap (bo qua FirebaseAuthGuard). */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);
