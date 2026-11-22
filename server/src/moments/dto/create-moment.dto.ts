import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';
import { MomentContentType } from '../entities/moment.entity';

/** Dang 1 moment. mediaUrl lay tu ket qua POST /upload (da qua Cloudinary + check 5s). */
export class CreateMomentDto {
  @ApiProperty({ enum: ['PHOTO', 'VIDEO'], description: 'Content type' })
  @IsIn(['PHOTO', 'VIDEO'])
  contentType!: MomentContentType;

  @ApiProperty({ description: 'Media URL returned by POST /upload' })
  @IsUrl()
  @IsNotEmpty()
  mediaUrl!: string;

  @ApiPropertyOptional({ description: 'Frame id applied to the moment' })
  @IsOptional()
  @IsString()
  frameId?: string;

  @ApiPropertyOptional({ description: 'Caption', maxLength: 500 })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  caption?: string;

  @ApiPropertyOptional({
    description:
      'Anti-DUPLICATE id (app generates a UUID, kept on retry): if a previous request' +
      'timed out but the post WENT through -> the retry returns the existing post instead of a copy',
    maxLength: 64,
  })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  clientRequestId?: string;
}
