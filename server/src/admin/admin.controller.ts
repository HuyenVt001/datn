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
  @ApiOperation({ summary: 'List users (search by email/name, paginated)' })
  listUsers(@Query() query: ListUsersDto) {
    return this.adminService.listUsers(query);
  }

  @Get('stats')
  @ApiOperation({ summary: 'Overview stats (user/moment/message/friendship/group)' })
  getStats() {
    return this.adminService.getStats();
  }

  @Get('stats/daily')
  @ApiOperation({ summary: 'Daily stats (moments + new users) for the dashboard chart' })
  getDailyStats(@Query() query: DailyStatsDto) {
    return this.adminService.getDailyStats(query.days);
  }

  @Get('moments')
  @ApiOperation({ summary: 'Latest posts (content moderation)' })
  listMoments(@Query() query: PaginationDto) {
    return this.adminService.listMoments(query);
  }

  @Delete('moments/:id')
  @ApiOperation({ summary: 'Delete a violating post (including views/reactions subcollections)' })
  async deleteMoment(@CurrentUser() actor: AuthUser, @Param('id') momentId: string) {
    const result = await this.adminService.deleteMoment(actor, momentId);
    return { message: 'Post deleted.', data: result };
  }

  @Get('logs')
  @ApiOperation({ summary: 'Admin action log (audit log, newest first)' })
  listLogs(@Query() query: PaginationDto) {
    return this.auditService.list(query);
  }

  @Get('ai-verifications')
  @ApiOperation({
    summary:
      '[AI quest] AI photo verification log (newest first) — with thumbnail, 12-class scores, thresholds, model',
    description:
      'Every time the server calls the AI service to check whether a posted photo contains the AI quest object (including misses/SKIPPED).' +
      'Source for production accuracy + threshold calibration. Filter by outcome / date / uid; in-memory pagination (scans at most the 500 newest logs).',
  })
  listAiVerifications(@Query() query: ListAiVerificationsDto) {
    return this.adminService.listAiVerifications(query);
  }

  @Patch('users/:uid/disabled')
  @ApiOperation({ summary: 'Disable / enable a user account (cannot disable yourself)' })
  setDisabled(
    @CurrentUser() actor: AuthUser,
    @Param('uid') uid: string,
    @Body() dto: SetDisabledDto,
  ) {
    return this.adminService.setUserDisabled(actor, uid, dto.disabled);
  }

  @Post('users/:uid/grant-admin')
  @ApiOperation({ summary: 'Grant admin rights to a user' })
  grantAdmin(@CurrentUser() actor: AuthUser, @Param('uid') uid: string) {
    return this.adminService.grantAdmin(actor, uid);
  }

  @Post('users/:uid/revoke-admin')
  @ApiOperation({ summary: 'Revoke admin rights (cannot revoke your own)' })
  revokeAdmin(@CurrentUser() actor: AuthUser, @Param('uid') uid: string) {
    return this.adminService.revokeAdmin(actor, uid);
  }
}
