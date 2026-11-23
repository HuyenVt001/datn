import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsOptional, IsString, MaxLength } from 'class-validator';
import { AttachmentType, MessageType } from '../entities/message.entity';

/**
 * Gui tin nhan: dien receiverId (1-1) HOAC groupId (nhom) — dung 1 trong 2.
 * content: van ban voi TEXT/EMOJI; URL file (tu /upload) voi VOICE/STICKER/PHOTO.
 */
export class SendMessageDto {
  @ApiPropertyOptional({ description: 'Recipient uid (1-1 message)' })
  @IsOptional()
  @IsString()
  receiverId?: string;

  @ApiPropertyOptional({ description: 'Group chat id (group message)' })
  @IsOptional()
  @IsString()
  groupId?: string;

  @ApiProperty({ enum: ['TEXT', 'VOICE', 'EMOJI', 'STICKER', 'PHOTO'] })
  @IsIn(['TEXT', 'VOICE', 'EMOJI', 'STICKER', 'PHOTO'])
  messageType!: MessageType;

  @ApiProperty({ description: 'Text content or file URL', maxLength: 2000 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(2000)
  content!: string;

  @ApiPropertyOptional({
    description: "Attached media URL (post replies carry the post's photo/video)",
    maxLength: 2000,
  })
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  attachmentUrl?: string;

  @ApiPropertyOptional({ enum: ['PHOTO', 'VIDEO'], description: 'Attached media type' })
  @IsOptional()
  @IsIn(['PHOTO', 'VIDEO'])
  attachmentType?: AttachmentType;

  @ApiPropertyOptional({
    description: 'id of the replied message (must be in the SAME conversation/group)',
    maxLength: 128,
  })
  @IsOptional()
  @IsString()
  @MaxLength(128)
  replyToId?: string;
}
