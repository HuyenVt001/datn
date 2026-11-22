import { ApiProperty } from '@nestjs/swagger';
import { ArrayMinSize, IsArray, IsString } from 'class-validator';

/** Them thanh vien vao nhom — nguoi duoc them PHAI la ban be cua nguoi moi. */
export class AddGroupMembersDto {
  @ApiProperty({ description: 'List of uids to add to the group', type: [String] })
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  memberIds!: string[];
}
