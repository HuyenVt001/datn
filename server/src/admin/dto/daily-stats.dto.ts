import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, Max, Min } from 'class-validator';

/** Query thong ke theo ngay cho bieu do dashboard. */
export class DailyStatsDto {
  @ApiPropertyOptional({ description: 'Number of recent days to include (1-30)', default: 7 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(30)
  days: number = 7;
}
