import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';

/** Doi ten / doi anh dai dien nhom — moi thanh vien deu doi duoc (kieu Messenger). */
export class UpdateGroupDto {
  @ApiPropertyOptional({ description: 'Ten nhom moi', maxLength: 100 })
  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  groupName?: string;

  @ApiPropertyOptional({ description: 'URL anh dai dien nhom (upload qua /upload truoc)' })
  @IsOptional()
  @IsUrl({ require_protocol: true })
  @MaxLength(500)
  avatar?: string;
}
