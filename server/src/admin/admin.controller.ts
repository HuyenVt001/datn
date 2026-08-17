import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuditService } from '../audit/audit.service';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Roles } from '../common/decorators/roles.decorator';
import { AdminJwtGuard } from '../common/guards/admin-jwt.guard';
import { RolesGuard } from '../common/guards/roles.guard';
import { PaginationDto } from '../common/dto/pagination.dto';
import { AdminService } from './admin.service';
import { DailyStatsDto } from './dto/daily-stats.dto';
import { ListAiVerificationsDto } from './dto/list-ai-verifications.dto';
import { ListUsersDto } from './dto/list-users.dto';
import { SetDisabledDto } from './dto/set-disabled.dto';

@ApiTags('admin')
@ApiBearerAuth('admin')
@UseGuards(AdminJwtGuard, RolesGuard)
@Roles('admin')
@Controller('admin')
export class AdminController {
  constructor(
    private readonly adminService: AdminService,
    private readonly auditService: AuditService,
  ) {}

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

  @Get('stats/daily')
  @ApiOperation({ summary: 'Thong ke theo ngay (moment + user moi) cho bieu do dashboard' })
  getDailyStats(@Query() query: DailyStatsDto) {
    return this.adminService.getDailyStats(query.days);
  }

  @Get('moments')
  @ApiOperation({ summary: 'Danh sach bai dang moi nhat (kiem duyet noi dung)' })
  listMoments(@Query() query: PaginationDto) {
    return this.adminService.listMoments(query);
  }

  @Delete('moments/:id')
  @ApiOperation({ summary: 'Xoa bai dang vi pham (kem subcollection views/reactions)' })
  async deleteMoment(@CurrentUser() actor: AuthUser, @Param('id') momentId: string) {
    const result = await this.adminService.deleteMoment(actor, momentId);
    return { message: 'Da xoa bai dang.', data: result };
  }

  @Get('logs')
  @ApiOperation({ summary: 'Nhat ky hanh dong admin (audit log, moi nhat truoc)' })
  listLogs(@Query() query: PaginationDto) {
    return this.auditService.list(query);
  }

  @Get('ai-verifications')
  @ApiOperation({
    summary:
      '[AI quest] Log AI xác minh ảnh (mới nhất trước) — kèm thumbnail, điểm 12 lớp, ngưỡng, model',
    description:
      'Mỗi lần server gọi AI service để kiểm tra ảnh đăng lên có chứa vật thể của quest AI (kể cả trượt/SKIPPED). ' +
      'Nguồn số liệu accuracy production + calibrate ngưỡng. Lọc theo outcome / date / uid; phân trang trong bộ nhớ (quét tối đa 500 log mới nhất).',
  })
  listAiVerifications(@Query() query: ListAiVerificationsDto) {
    return this.adminService.listAiVerifications(query);
  }

  @Patch('users/:uid/disabled')
  @ApiOperation({ summary: 'Khoa / mo khoa tai khoan nguoi dung (khong tu khoa chinh minh)' })
  setDisabled(
    @CurrentUser() actor: AuthUser,
    @Param('uid') uid: string,
    @Body() dto: SetDisabledDto,
  ) {
    return this.adminService.setUserDisabled(actor, uid, dto.disabled);
  }

  @Post('users/:uid/grant-admin')
  @ApiOperation({ summary: 'Cap quyen admin cho nguoi dung' })
  grantAdmin(@CurrentUser() actor: AuthUser, @Param('uid') uid: string) {
    return this.adminService.grantAdmin(actor, uid);
  }

  @Post('users/:uid/revoke-admin')
  @ApiOperation({ summary: 'Thu hoi quyen admin (khong tu thu quyen chinh minh)' })
  revokeAdmin(@CurrentUser() actor: AuthUser, @Param('uid') uid: string) {
    return this.adminService.revokeAdmin(actor, uid);
  }
}
