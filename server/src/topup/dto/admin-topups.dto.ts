import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Matches, Max, Min } from 'class-validator';
import { TOPUP_ORDER_STATUSES, TopupOrderStatus } from '../entities/topup-order.entity';

/** Bo loc trang lich su nap cua admin (tat ca deu tuy chon). */
export class AdminTopupsDto {
  @ApiPropertyOptional({ description: 'Lọc theo uid người nạp' })
  @IsOptional()
  @IsString()
  uid?: string;

  @ApiPropertyOptional({ description: 'Lọc theo trạng thái đơn', enum: TOPUP_ORDER_STATUSES })
  @IsOptional()
  @IsIn(TOPUP_ORDER_STATUSES)
  status?: TopupOrderStatus;

  @ApiPropertyOptional({ description: 'Lọc theo ngày UTC, dạng YYYY-MM-DD', example: '2026-08-05' })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/, { message: 'Ngay phai co dang YYYY-MM-DD.' })
  date?: string;

  @ApiPropertyOptional({ description: 'Số bản ghi tối đa (mặc định 200)', default: 200 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(1000)
  limit?: number;
}
