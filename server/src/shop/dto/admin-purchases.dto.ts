import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Matches, Max, Min } from 'class-validator';
import { ITEM_TYPES, ItemType } from '../entities/shop-item.entity';

/** Bo loc trang lich su mua cua admin (tat ca deu tuy chon). */
export class AdminPurchasesDto {
  @ApiPropertyOptional({ description: 'Loc theo uid nguoi mua' })
  @IsOptional()
  @IsString()
  uid?: string;

  @ApiPropertyOptional({ description: 'Loc theo loai mat hang', enum: ITEM_TYPES })
  @IsOptional()
  @IsIn(ITEM_TYPES)
  itemType?: ItemType;

  @ApiPropertyOptional({ description: 'Loc theo ngay UTC, dang YYYY-MM-DD', example: '2026-11-15' })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, { message: 'Ngay phai co dang YYYY-MM-DD.' })
  date?: string;

  @ApiPropertyOptional({ description: 'So ban ghi toi da (mac dinh 200)', default: 200 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(1000)
  limit?: number;
}
