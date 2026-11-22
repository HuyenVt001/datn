import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/** Dang ky / go FCM token (nhan push notification). */
export class FcmTokenDto {
  @ApiProperty({ description: 'Device FCM registration token' })
  @IsString()
  @IsNotEmpty()
  token!: string;
}
