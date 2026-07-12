import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

/** Tha emoji len 1 moment (reaction bay len man hinh). */
export class ReactDto {
  @ApiProperty({ description: 'Loai emoji (vd: ❤️, 🔥, 😂)', maxLength: 16 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(16)
  emojiType!: string;
}
