import { ApiProperty } from '@nestjs/swagger';

/**
 * Envelope chuan cho moi response thanh cong.
 * Duoc boc tu dong boi ResponseInterceptor — controller chi tra `data`.
 */
export class ApiResponseDto<T> {
  @ApiProperty({ example: true, description: 'Whether the request succeeded' })
  success!: boolean;

  @ApiProperty({ example: 200, description: 'HTTP status code' })
  statusCode!: number;

  @ApiProperty({ example: 'OK', description: 'Message' })
  message!: string;

  @ApiProperty({ description: 'Response data' })
  data!: T;
}
