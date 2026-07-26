import { ApiProperty } from '@nestjs/swagger';

/**
 * Envelope chuan cho moi response thanh cong.
 * Duoc boc tu dong boi ResponseInterceptor — controller chi tra `data`.
 */
export class ApiResponseDto<T> {
  @ApiProperty({ example: true, description: 'Request thanh cong hay khong' })
  success!: boolean;

  @ApiProperty({ example: 200, description: 'HTTP status code' })
  statusCode!: number;

  @ApiProperty({ example: 'OK', description: 'Thong bao (tieng Viet)' })
  message!: string;

  @ApiProperty({ description: 'Du lieu tra ve' })
  data!: T;
}
