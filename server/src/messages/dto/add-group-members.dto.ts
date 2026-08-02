import { ApiProperty } from '@nestjs/swagger';
import { ArrayMinSize, IsArray, IsString } from 'class-validator';

/** Them thanh vien vao nhom — nguoi duoc them PHAI la ban be cua nguoi moi. */
export class AddGroupMembersDto {
  @ApiProperty({ description: 'Danh sach uid can them vao nhom', type: [String] })
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  memberIds!: string[];
}
