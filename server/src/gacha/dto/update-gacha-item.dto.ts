import { ApiPropertyOptional } from '@nestjs/swagger';
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
import { ITEM_RARITIES, ItemRarity } from '../entities/gacha-item.entity';

/**
 * Admin sua vat pham. KHONG cho doi `itemType` va `refId` — hai field nay tro
 * toi asset that (frameId / skinId / effectId); doi chung = vat pham nguoi choi
 * DA so huu bong tro thanh thu khac.
 */
export class UpdateGachaItemDto {
  @ApiPropertyOptional({ description: 'Ten vat pham', maxLength: 100 })
  @IsOptional()
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  itemName?: string;

  @ApiPropertyOptional({ description: 'Pham chat', enum: ITEM_RARITIES })
  @IsOptional()
  @IsIn(ITEM_RARITIES)
  rarity?: ItemRarity;

  @ApiPropertyOptional({ description: 'Anh dai dien (tu /upload/admin hoac link ngoai)' })
  @IsOptional()
  @IsUrl()
  imageUrl?: string;

  @ApiPropertyOptional({ description: 'Tat = khong quay ra nua' })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiPropertyOptional({ description: 'Thu tu hien thi (nho len truoc)' })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}
