import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/**
 * Admin dang nhap tren web React bang Firebase Auth (email/password) -> lay Firebase ID token,
 * gui token do len day. Server verify + kiem tra quyen admin -> phat JWT rieng.
 */
export class AdminLoginDto {
  @ApiProperty({ description: 'Firebase ID token cua tai khoan admin' })
  @IsString()
  @IsNotEmpty()
  idToken!: string;
}
