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
import { ITEM_RARITIES, ITEM_TYPES, ItemRarity, ItemType } from '../entities/gacha-item.entity';

/**
 * Admin them vat pham vao kho quay.
 *
 * ⚠️ CHI tao duoc `itemType = FRAME`. Skin va hieu ung touch co asset nam TRONG
 * APK (khop theo `refId` = skinId / effectId), tao moi tu trang admin se ra vat
 * pham quay trung duoc nhung app khong co gi de hien. Rang buoc nay enforce o
 * `GachaService.createItem`.
 */
export class CreateGachaItemDto {
  @ApiProperty({ description: 'Ten vat pham hien tren the ket qua quay', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  itemName!: string;

  @ApiProperty({ description: 'Loai vat pham — chi cho phep FRAME', enum: ITEM_TYPES })
  @IsIn(ITEM_TYPES)
  itemType!: ItemType;

  @ApiProperty({ description: 'Pham chat (khung anh thuong la R)', enum: ITEM_RARITIES })
  @IsIn(ITEM_RARITIES)
  rarity!: ItemRarity;

  @ApiProperty({ description: 'Tro toi vat pham that: FRAME -> frameId' })
  @IsString()
  @IsNotEmpty()
  refId!: string;

  @ApiPropertyOptional({ description: 'Anh dai dien (tu /upload/admin hoac link ngoai)' })
  @IsOptional()
  @IsUrl()
  imageUrl?: string;

  @ApiPropertyOptional({ description: 'Tat = khong quay ra nua (mac dinh bat)', default: true })
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
