import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

/** Tha reaction len 1 tin nhan — bam lai cung emoji = go reaction (toggle). */
export class ReactMessageDto {
  @ApiProperty({ description: 'Emoji reaction (vd 💛)', maxLength: 16 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(16)
  emoji!: string;
}
