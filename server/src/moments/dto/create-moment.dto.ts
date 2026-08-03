import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';
import { MomentContentType } from '../entities/moment.entity';

/** Dang 1 moment. mediaUrl lay tu ket qua POST /upload (da qua Cloudinary + check 5s). */
export class CreateMomentDto {
  @ApiProperty({ enum: ['PHOTO', 'VIDEO'], description: 'Loai noi dung' })
  @IsIn(['PHOTO', 'VIDEO'])
  contentType!: MomentContentType;

  @ApiProperty({ description: 'URL media tra ve tu POST /upload' })
  @IsUrl()
  @IsNotEmpty()
  mediaUrl!: string;

  @ApiPropertyOptional({ description: 'Id khung anh ap len moment' })
  @IsOptional()
  @IsString()
  frameId?: string;

  @ApiPropertyOptional({ description: 'Chu thich', maxLength: 500 })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  caption?: string;

  @ApiPropertyOptional({
    description:
      'Id chong dang TRUNG (app sinh UUID, giu nguyen khi retry): request truoc ' +
      'timeout nhung bai DA len -> retry tra lai bai cu thay vi tao ban sao',
    maxLength: 64,
  })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  clientRequestId?: string;
}
