import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Matches, Max, Min } from 'class-validator';
import { ITEM_TYPES, ItemType } from '../entities/shop-item.entity';

/** Bo loc trang lich su mua cua admin (tat ca deu tuy chon). */
export class AdminPurchasesDto {
  @ApiPropertyOptional({ description: 'Filter by buyer uid' })
  @IsOptional()
  @IsString()
  uid?: string;

  @ApiPropertyOptional({ description: 'Filter by item type', enum: ITEM_TYPES })
  @IsOptional()
  @IsIn(ITEM_TYPES)
  itemType?: ItemType;

  @ApiPropertyOptional({
    description: 'Filter by Vietnam date (UTC+7), format YYYY-MM-DD',
    example: '2026-11-15',
  })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, { message: 'Date must be in YYYY-MM-DD format.' })
  date?: string;

  @ApiPropertyOptional({ description: 'Max records (default 200)', default: 200 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(1000)
  limit?: number;
}
