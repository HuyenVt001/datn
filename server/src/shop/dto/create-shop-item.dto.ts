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
  @ApiProperty({ description: 'Ten mat hang hien trong Shop', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  itemName!: string;

  @ApiProperty({ description: 'Loai mat hang', enum: ITEM_TYPES })
  @IsIn(ITEM_TYPES)
  itemType!: ItemType;

  @ApiProperty({ description: 'FRAME -> frameId; SKIN/EFFECT -> id so trong app (vd "1")' })
  @IsString()
  @IsNotEmpty()
  refId!: string;

  @ApiPropertyOptional({
    description: 'Gia ban (Astrite). Bo trong = gia mac dinh theo loai (320 / 4800 / 1600)',
  })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  price?: number;

  @ApiPropertyOptional({ description: 'Anh dai dien (tu /upload/admin hoac link ngoai)' })
  @IsOptional()
  @IsUrl()
  imageUrl?: string;

  @ApiPropertyOptional({ description: 'Mo ban ngay (mac dinh bat)', default: true })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiPropertyOptional({ description: 'Thu tu hien thi (nho len truoc)', default: 0 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}
