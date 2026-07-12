import { ApiProperty } from '@nestjs/swagger';
import { ArrayMinSize, IsArray, IsNotEmpty, IsString, MaxLength } from 'class-validator';

/** Tao nhom chat. Nguoi tao tu dong la thanh vien; tong thanh vien <= 20. */
export class CreateGroupDto {
  @ApiProperty({ description: 'Ten nhom', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  groupName!: string;

  @ApiProperty({
    description: 'Danh sach uid thanh vien (khong can gom nguoi tao)',
    type: [String],
  })
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  memberIds!: string[];
}
