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
  @ApiProperty({ description: "Package name shown in the app's top-up popup", maxLength: 60 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(60)
  name!: string;

  @ApiProperty({ description: 'Astrite credited to the wallet on successful payment', minimum: 1 })
  @Type(() => Number)
  @IsInt()
  @Min(1)
  astrite!: number;

  @ApiProperty({
    description: 'Price in VND (integer). PayOS requires at least 1,000 VND.',
    minimum: 1000,
  })
  @Type(() => Number)
  @IsInt()
  @Min(1000, { message: 'Package price must be at least 1,000 VND (PayOS limit).' })
  priceVnd!: number;

  @ApiPropertyOptional({
    description: 'Off = hidden from the top-up popup (default on)',
    default: true,
  })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiPropertyOptional({
    description: 'Marks a package used for testing (just so admins can spot it)',
    default: false,
  })
  @IsOptional()
  @IsBoolean()
  isTest?: boolean;

  @ApiPropertyOptional({ description: 'Display order (lower first)', default: 0 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  sortOrder?: number;
}
