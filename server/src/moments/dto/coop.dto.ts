import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';

/** Gui loi moi chup chung cho 1 nguoi ban (kem nua anh cua minh). */
export class CreateCoopInviteDto {
  @ApiProperty({ description: 'uid nguoi ban duoc moi chup chung' })
  @IsString()
  @IsNotEmpty()
  friendUid!: string;

  @ApiProperty({ description: 'URL nua anh cua minh (tu POST /upload)' })
  @IsUrl()
  mediaUrl!: string;
}

/** Chap nhan loi moi: nop nua anh con lai (+ caption tuy chon). */
export class AcceptCoopInviteDto {
  @ApiProperty({ description: 'URL nua anh cua minh (tu POST /upload)' })
  @IsUrl()
  mediaUrl!: string;

  @ApiPropertyOptional({ description: 'Caption cho khoanh khac chung', maxLength: 500 })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  caption?: string;
}
