import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsIn, IsOptional, IsString, Matches, MaxLength } from 'class-validator';
import { PaginationDto } from '../../common/dto/pagination.dto';

export const AI_VERIFICATION_OUTCOMES = ['MATCHED', 'NOT_MATCHED', 'SKIPPED'] as const;
export type AiVerificationOutcome = (typeof AI_VERIFICATION_OUTCOMES)[number];

/** Query log AI xac minh anh quest (trang admin, 2026-08-16). */
export class ListAiVerificationsDto extends PaginationDto {
  @ApiPropertyOptional({ enum: AI_VERIFICATION_OUTCOMES, description: 'Filter by outcome' })
  @IsOptional()
  @IsIn(AI_VERIFICATION_OUTCOMES)
  outcome?: AiVerificationOutcome;

  @ApiPropertyOptional({
    description: 'Filter by quest date (YYYY-MM-DD, UTC)',
    example: '2026-08-16',
  })
  @IsOptional()
  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  date?: string;

  @ApiPropertyOptional({ description: 'Filter by user uid' })
  @IsOptional()
  @IsString()
  @MaxLength(128)
  uid?: string;
}
