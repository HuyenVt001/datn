import { ApiProperty } from '@nestjs/swagger';
import { ArrayMinSize, IsArray, IsNotEmpty, IsString, MaxLength } from 'class-validator';

/** Tao nhom chat. Nguoi tao tu dong la thanh vien; tong thanh vien <= 20. */
export class CreateGroupDto {
  @ApiProperty({ description: 'Group name', maxLength: 100 })
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  groupName!: string;

  @ApiProperty({
    description: 'Member uid list (creator not required)',
    type: [String],
  })
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  memberIds!: string[];
}
