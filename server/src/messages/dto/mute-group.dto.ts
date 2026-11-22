import { ApiProperty } from '@nestjs/swagger';
import { IsBoolean } from 'class-validator';

/** Bat/tat thong bao nhom cho RIENG minh (khong anh huong thanh vien khac). */
export class MuteGroupDto {
  @ApiProperty({ description: 'true = mute group notifications, false = unmute' })
  @IsBoolean()
  muted!: boolean;
}
