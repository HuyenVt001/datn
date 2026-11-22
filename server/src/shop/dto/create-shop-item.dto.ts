import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  Min,
} from 'class-validator';
import { ITEM_TYPES, ItemType } from '../entities/shop-item.entity';

/**
 * Admin niem yet mat hang moi.
 * - FRAME: `refId` = frameId cua khung co unlockType = SHOP.
 * - SKIN / EFFECT: `refId` = so nguyen >= 1, PHAI co san trong APK.
 * Rang buoc theo loai + chong trung (itemType, refId) kiem o ShopService.createItem.
 */
export class CreateShopItemDto {
  @ApiProperty({ description: 'Item name shown in the Shop', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  itemName!: string;

  @ApiProperty({ description: 'Item type', enum: ITEM_TYPES })
  @IsIn(ITEM_TYPES)
  itemType!: ItemType;

  @ApiProperty({ description: 'FRAME -> frameId; SKIN/EFFECT -> numeric id in the app (e.g. "1")' })
  @IsString()
  @IsNotEmpty()
  refId!: string;

  @ApiPropertyOptional({
    description: 'Price (Astrite). Empty = default price by type (320 / 4800 / 1600)',
  })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  price?: number;

  @ApiPropertyOptional({ description: 'Thumbnail (from /upload/admin or an external link)' })
  @IsOptional()
  @IsUrl()
  imageUrl?: string;

  @ApiPropertyOptional({ description: 'On sale immediately (default on)', default: true })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiPropertyOptional({ description: 'Display order (lower first)', default: 0 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}
