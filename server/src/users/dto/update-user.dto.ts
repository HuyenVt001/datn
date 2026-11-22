import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, Matches, MaxLength } from 'class-validator';

/** Cap nhat ho so — cho phep doi ten hien thi, avatar va ngay sinh (theo nghiep vu). */
export class UpdateUserDto {
  @ApiPropertyOptional({ description: 'Display name' })
  @IsOptional()
  @IsString()
  @MaxLength(100)
  fullName?: string;

  @ApiPropertyOptional({ description: 'Avatar URL (uploaded via the server)' })
  @IsOptional()
  @IsString()
  avatar?: string;

  @ApiPropertyOptional({ description: 'Birthday (ISO yyyy-MM-dd)' })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, {
    message: 'Birthday must be in yyyy-MM-dd format.',
  })
  birthday?: string;
}
