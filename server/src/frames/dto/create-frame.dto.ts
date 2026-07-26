import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsNotEmpty, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';
import { STREAK_MILESTONES } from '../../common/constants';

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

  @ApiPropertyOptional({
    description:
      'Moc streak (3/7/14/30) ma khung nay la phan thuong; bo trong = khung thuong quest',
    enum: STREAK_MILESTONES,
  })
  @IsOptional()
  @IsIn(STREAK_MILESTONES)
  milestone?: number;
}
