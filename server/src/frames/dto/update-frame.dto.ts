import { PartialType } from '@nestjs/swagger';
import { CreateFrameDto } from './create-frame.dto';

/**
 * Admin sua khung anh — moi field deu tuy chon (PartialType tu Create).
 * Doi unlockType thi PHAI gui kem unlockValue phu hop (neu loai moi can nguong) —
 * service khong tu mang nguong cua loai cu sang loai moi.
 */
export class UpdateFrameDto extends PartialType(CreateFrameDto) {}
