import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, Min } from 'class-validator';

/**
 * Gia lap PayOS bao "da thanh toan" — **chi chay khi `NODE_ENV !== 'production'`**.
 *
 * Ly do ton tai: goi FREE-100 cua PayOS chi cho 100 GIAO DICH THANH CONG, moi
 * lan test that tieu 1 giao dich va khong hoan lai duoc. Endpoint nay di dung
 * duong ma webhook that di (verify chu ky -> transaction -> cong Astrite ->
 * ghi so cai), nen test bang no van phu dung logic (GACHA_PLAN.md muc 4.5).
 *
 * Hai cach dung:
 *  - `{ packageId }`  -> tao 1 don gia lap roi thanh toan luon (khong goi PayOS).
 *  - `{ orderCode }`  -> phat lai webhook cho don da co. Goi 3 lan cung 1 ma de
 *                        kiem tra idempotent: so du chi duoc tang DUNG 1 lan.
 */
export class SimulateTopupDto {
  @ApiPropertyOptional({ description: 'Tạo đơn giả lập từ gói này rồi thanh toán luôn' })
  @IsOptional()
  @IsString()
  packageId?: string;

  @ApiPropertyOptional({ description: 'Phát lại webhook cho đơn đã tồn tại' })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  orderCode?: number;
}
