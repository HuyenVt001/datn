import { ApiPropertyOptional, OmitType, PartialType } from '@nestjs/swagger';
import { IsIn, IsOptional } from 'class-validator';
import { STREAK_MILESTONES } from '../../common/constants';
import { CreateFrameDto } from './create-frame.dto';

/**
 * Admin sua khung anh — moi field deu tuy chon (PartialType tu Create).
 * milestone khai bao lai (Omit khoi base) de nhan them `null` = XOA moc streak
 * (chuyen ve khung thuong quest); khong gui field = giu nguyen.
 */
export class UpdateFrameDto extends PartialType(OmitType(CreateFrameDto, ['milestone'] as const)) {
  @ApiPropertyOptional({
    description: 'Moc streak (3/7/14/30); null = xoa moc, bo qua = giu nguyen',
    enum: STREAK_MILESTONES,
    nullable: true,
  })
  @IsOptional()
  @IsIn(STREAK_MILESTONES)
  milestone?: number | null;
}
