import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Matches, Max, Min } from 'class-validator';
import { ITEM_RARITIES, RollTier } from '../entities/gacha-item.entity';

/** Bac loc duoc o trang lich su: 3 bac vat pham + bac N (chi ra Astrite). */
const FILTER_TIERS: RollTier[] = ['N', ...ITEM_RARITIES];

/** Bo loc trang lich su quay cua admin (tat ca deu tuy chon). */
export class AdminRollsDto {
  @ApiPropertyOptional({ description: 'Loc theo uid nguoi quay' })
  @IsOptional()
  @IsString()
  uid?: string;

  @ApiPropertyOptional({
    description: 'Chi lay luot quay CO it nhat 1 ket qua bac nay',
    enum: FILTER_TIERS,
  })
  @IsOptional()
  @IsIn(FILTER_TIERS)
  tier?: RollTier;

  @ApiPropertyOptional({ description: 'Loc theo ngay UTC, dang YYYY-MM-DD', example: '2026-08-05' })
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
