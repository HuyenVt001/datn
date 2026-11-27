import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Matches, Max, Min } from 'class-validator';
import { TOPUP_ORDER_STATUSES, TopupOrderStatus } from '../entities/topup-order.entity';

/** Bo loc trang lich su nap cua admin (tat ca deu tuy chon). */
export class AdminTopupsDto {
  @ApiPropertyOptional({ description: 'Filter by top-up user uid' })
  @IsOptional()
  @IsString()
  uid?: string;

  @ApiPropertyOptional({ description: 'Filter by order status', enum: TOPUP_ORDER_STATUSES })
  @IsOptional()
  @IsIn(TOPUP_ORDER_STATUSES)
  status?: TopupOrderStatus;

  @ApiPropertyOptional({
    description: 'Filter by Vietnam date (UTC+7), format YYYY-MM-DD',
    example: '2026-08-05',
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
