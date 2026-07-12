import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, Max, Min } from 'class-validator';

/** Query phan trang dung chung cho cac endpoint list. */
export class PaginationDto {
  @ApiPropertyOptional({ default: 1, minimum: 1, description: 'Trang hien tai' })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page = 1;

  @ApiPropertyOptional({ default: 20, minimum: 1, maximum: 100, description: 'So phan tu / trang' })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit = 20;
}

/** Ket qua phan trang chuan (nam trong `data` cua envelope). */
export interface PaginatedResult<T> {
  items: T[];
  page: number;
  limit: number;
  total: number;
}
