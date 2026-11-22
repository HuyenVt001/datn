import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/** Body mua vat pham — CHI nhan itemId, gia luon tra o server. */
export class BuyItemDto {
  @ApiProperty({ description: 'Shop item ID (shopItems)' })
  @IsString()
  @IsNotEmpty()
  itemId!: string;
}
