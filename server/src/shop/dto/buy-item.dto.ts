import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/** Body mua vat pham — CHI nhan itemId, gia luon tra o server. */
export class BuyItemDto {
  @ApiProperty({ description: 'ID mat hang trong Shop (shopItems)' })
  @IsString()
  @IsNotEmpty()
  itemId!: string;
}
