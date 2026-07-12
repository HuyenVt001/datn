import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, MaxLength } from 'class-validator';

/** Cap nhat ho so — chi cho phep doi ten hien thi va avatar (theo nghiep vu). */
export class UpdateUserDto {
  @ApiPropertyOptional({ description: 'Ten hien thi' })
  @IsOptional()
  @IsString()
  @MaxLength(100)
  fullName?: string;

  @ApiPropertyOptional({ description: 'URL anh dai dien (da upload qua server)' })
  @IsOptional()
  @IsString()
  avatar?: string;
}
