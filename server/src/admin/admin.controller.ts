import { Body, Controller, Get, Param, Patch, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { AdminService } from './admin.service';
import { ListUsersDto } from './dto/list-users.dto';
import { SetDisabledDto } from './dto/set-disabled.dto';

@ApiTags('admin')
@ApiBearerAuth('admin')
@UseGuards(AdminJwtGuard, RolesGuard)
@Roles('admin')
@Controller('admin')
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  @Get('users')
  @ApiOperation({ summary: 'Danh sach nguoi dung (tim kiem theo email/ten, phan trang)' })
  listUsers(@Query() query: ListUsersDto) {
    return this.adminService.listUsers(query);
  }

  @Get('stats')
  @ApiOperation({ summary: 'Thong ke tong quan (user/moment/message/friendship/group)' })
  getStats() {
    return this.adminService.getStats();
  }

  @Patch('users/:uid/disabled')
  @ApiOperation({ summary: 'Khoa / mo khoa tai khoan nguoi dung' })
  setDisabled(@Param('uid') uid: string, @Body() dto: SetDisabledDto) {
    return this.adminService.setUserDisabled(uid, dto.disabled);
  }

  @Post('users/:uid/grant-admin')
  @ApiOperation({ summary: 'Cap quyen admin cho nguoi dung' })
  grantAdmin(@Param('uid') uid: string) {
    return this.adminService.grantAdmin(uid);
  }
}
