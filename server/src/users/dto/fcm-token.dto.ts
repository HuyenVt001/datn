import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/** Dang ky / go FCM token (nhan push notification). */
export class FcmTokenDto {
  @ApiProperty({ description: 'FCM registration token cua thiet bi' })
  @IsString()
  @IsNotEmpty()
  token!: string;
}
