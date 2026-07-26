import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, Matches, MaxLength } from 'class-validator';

/** Cap nhat ho so — cho phep doi ten hien thi, avatar va ngay sinh (theo nghiep vu). */
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

  @ApiPropertyOptional({ description: 'Ngay sinh (ISO yyyy-MM-dd)' })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, {
    message: 'Ngày sinh phải theo định dạng yyyy-MM-dd.',
  })
  birthday?: string;
}
