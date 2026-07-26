import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, MaxLength } from 'class-validator';
import { PaginationDto } from '../../common/dto/pagination.dto';

/** Query danh sach nguoi dung (tim theo email / ten hien thi). */
export class ListUsersDto extends PaginationDto {
  @ApiPropertyOptional({ description: 'Tu khoa tim kiem (email hoac ten)' })
  @IsOptional()
  @IsString()
  @MaxLength(100)
  search?: string;
}
