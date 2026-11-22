import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, IsUrl } from 'class-validator';

/** Gui loi moi chup chung cho 1 nguoi ban (KHONG kem anh — redesign 2026-08-02). */
export class CreateCoopInviteDto {
  @ApiProperty({ description: 'uid of the friend invited to the co-op capture' })
  @IsString()
  @IsNotEmpty()
  friendUid!: string;
}

/** Nop nua anh cua minh o man chup coop (sau khi loi moi duoc chap nhan). */
export class SubmitCoopMediaDto {
  @ApiProperty({ description: 'URL of your half photo (from POST /upload)' })
  @IsUrl()
  mediaUrl!: string;
}
