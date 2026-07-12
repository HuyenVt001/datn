import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsOptional, IsString, MaxLength } from 'class-validator';
import { MessageType } from '../entities/message.entity';

/**
 * Gui tin nhan: dien receiverId (1-1) HOAC groupId (nhom) — dung 1 trong 2.
 * content: van ban voi TEXT/EMOJI; URL file (tu /upload) voi VOICE/STICKER/PHOTO.
 */
export class SendMessageDto {
  @ApiPropertyOptional({ description: 'uid nguoi nhan (tin 1-1)' })
  @IsOptional()
  @IsString()
  receiverId?: string;

  @ApiPropertyOptional({ description: 'id nhom chat (tin nhom)' })
  @IsOptional()
  @IsString()
  groupId?: string;

  @ApiProperty({ enum: ['TEXT', 'VOICE', 'EMOJI', 'STICKER', 'PHOTO'] })
  @IsIn(['TEXT', 'VOICE', 'EMOJI', 'STICKER', 'PHOTO'])
  messageType!: MessageType;

  @ApiProperty({ description: 'Noi dung van ban hoac URL file', maxLength: 2000 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(2000)
  content!: string;
}
