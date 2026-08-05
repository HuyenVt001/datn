import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsBoolean,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  MaxLength,
  Min,
} from 'class-validator';

/** Admin them goi nap moi. */
export class CreateTopupPackageDto {
  @ApiProperty({ description: 'Tên gói hiện trong popup nạp của app', maxLength: 60 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(60)
  name!: string;

  @ApiProperty({ description: 'Số Astrite cộng vào ví khi thanh toán thành công', minimum: 1 })
  @Type(() => Number)
  @IsInt()
  @Min(1)
  astrite!: number;

  @ApiProperty({
    description: 'Giá tiền VND (số nguyên). PayOS yêu cầu tối thiểu 1.000đ.',
    minimum: 1000,
  })
  @Type(() => Number)
  @IsInt()
  @Min(1000, { message: 'Gia goi nap toi thieu 1000d (gioi han cua PayOS).' })
  priceVnd!: number;

  @ApiPropertyOptional({ description: 'Tắt = ẩn khỏi popup nạp (mặc định bật)', default: true })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiPropertyOptional({
    description: 'Đánh dấu gói dùng để kiểm thử (chỉ để admin dễ nhận ra)',
    default: false,
  })
  @IsOptional()
  @IsBoolean()
  isTest?: boolean;

  @ApiPropertyOptional({ description: 'Thứ tự hiển thị (nhỏ lên trước)', default: 0 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}
