import { OmitType, PartialType } from '@nestjs/swagger';
import { CreateShopItemDto } from './create-shop-item.dto';

/**
 * Admin sua mat hang. KHONG cho doi `itemType` va `refId` — hai field nay tro
 * toi vat pham that; doi chung = thu nguoi choi DA mua bong thanh thu khac.
 */
export class UpdateShopItemDto extends PartialType(
  OmitType(CreateShopItemDto, ['itemType', 'refId'] as const),
) {}
