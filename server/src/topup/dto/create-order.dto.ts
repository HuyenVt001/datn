import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';

/**
 * Tao don nap.
 *
 * ⚠️ CO DUY NHAT `packageId` — va se mai mai nhu vay. So Astrite va so tien
 * deu tra tu `topupPackages` o server. Neu DTO nay nhan them `amount` hay
 * `astrite` thi bat ky ai cung tu khai duoc "toi tra 1d, cong cho toi 5 trieu
 * Astrite" (GACHA_PLAN.md muc 4.2).
 */
export class CreateTopupOrderDto {
  @ApiProperty({ description: 'Id gói nạp lấy từ GET /topup/packages' })
  @IsString()
  @IsNotEmpty()
  packageId!: string;
}
