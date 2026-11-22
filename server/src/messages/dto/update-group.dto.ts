import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';

/** Doi ten / doi anh dai dien nhom — moi thanh vien deu doi duoc (kieu Messenger). */
export class UpdateGroupDto {
  @ApiPropertyOptional({ description: 'New group name', maxLength: 100 })
  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  groupName?: string;

  @ApiPropertyOptional({ description: 'Group avatar URL (upload via /upload first)' })
  @IsOptional()
  @IsUrl({ require_protocol: true })
  @MaxLength(500)
  avatar?: string;
}
