import { ApiProperty } from '@nestjs/swagger';
import { IsIn, IsInt } from 'class-validator';
import { GACHA_TEN_TIMES } from '../../common/constants';

export class RollDto {
  @ApiProperty({
    description: 'Số lần quay: 1 (giá 160 Astrite) hoặc 10 (giá 1440 Astrite, giảm 10%)',
    enum: [1, GACHA_TEN_TIMES],
    example: 1,
  })
  @IsInt()
  @IsIn([1, GACHA_TEN_TIMES], { message: `So lan quay chi duoc la 1 hoac ${GACHA_TEN_TIMES}.` })
  times!: number;
}
