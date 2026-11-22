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
  @ApiProperty({ description: 'Frame name', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  frameName!: string;

  @ApiPropertyOptional({ description: 'Frame image URL (from /upload or an external link)' })
  @IsOptional()
  @IsUrl()
  imageUrl?: string;

  @ApiPropertyOptional({
    description: 'Unlock condition; empty = SHOP (frame sold in the Shop, default price 320)',
    enum: UNLOCK_TYPES,
    default: 'SHOP',
  })
  @IsOptional()
  @IsIn(UNLOCK_TYPES)
  unlockType?: UnlockType;

  @ApiPropertyOptional({
    description: 'Condition threshold N (streak milestone / post count / friend count)',
  })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  unlockValue?: number;
}
