import { PartialType } from '@nestjs/swagger';
import { CreateTopupPackageDto } from './create-package.dto';

/**
 * Admin sua goi nap — moi field cua [CreateTopupPackageDto] deu tuy chon.
 *
 * Sua `astrite` / `priceVnd` CHI anh huong don tao TU LUC NAY tro di: don da
 * tao chup lai so tien va so Astrite cua chinh no (xem `TopupOrder`), nen
 * nguoi dang giua chung luong thanh toan khong bi doi gia.
 */
export class UpdateTopupPackageDto extends PartialType(CreateTopupPackageDto) {}
