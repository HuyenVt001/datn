import { ApiProperty } from '@nestjs/swagger';
import { IsBoolean } from 'class-validator';

export class SetDisabledDto {
  @ApiProperty({ description: 'true = disable account, false = enable' })
  @IsBoolean()
  disabled!: boolean;
}
