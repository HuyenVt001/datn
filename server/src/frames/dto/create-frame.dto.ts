import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsIn,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  Min,
} from 'class-validator';
import { UNLOCK_TYPES, UnlockType } from '../entities/frame.entity';

/**
 * Admin them khung anh moi vao catalog.
 * Rang buoc unlockValue theo tung unlockType kiem tra o FramesService.assertUnlockRule
 * (STREAK_MILESTONE ∈ 3/7/14/30, POST_COUNT >= 1, FRIEND_COUNT 1..20).
 */
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
    description: 'Dieu kien mo khoa; bo trong = QUEST_RANDOM (thuong quest ngau nhien)',
    enum: UNLOCK_TYPES,
    default: 'QUEST_RANDOM',
  })
  @IsOptional()
  @IsIn(UNLOCK_TYPES)
  unlockType?: UnlockType;

  @ApiPropertyOptional({
    description: 'Nguong N cua dieu kien (moc streak / so bai dang / so ban be)',
  })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  unlockValue?: number;
}
