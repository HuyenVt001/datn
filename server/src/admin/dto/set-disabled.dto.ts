import { ApiProperty } from '@nestjs/swagger';
import { IsBoolean } from 'class-validator';

export class SetDisabledDto {
  @ApiProperty({ description: 'true = khoa tai khoan, false = mo khoa' })
  @IsBoolean()
  disabled!: boolean;
}
