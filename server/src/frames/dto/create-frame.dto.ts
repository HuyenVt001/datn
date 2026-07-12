import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';

/** Admin them khung anh moi vao catalog. */
export class CreateFrameDto {
  @ApiProperty({ description: 'Ten khung anh', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  frameName!: string;

  @ApiPropertyOptional({ description: 'URL anh khung (tu /upload hoac link ngoai)' })
  @IsOptional()
  @IsUrl()
  imageUrl?: string;
}
