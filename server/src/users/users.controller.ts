import { Body, Controller, Delete, Get, Param, Patch, Post, UseGuards } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiHeader,
  ApiOperation,
  ApiResponse,
  ApiSecurity,
  ApiTags,
} from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Public } from '../common/decorators/public.decorator';
import { CRON_SECRET_HEADER, CronSecretGuard } from '../common/guards/cron-secret.guard';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { FcmTokenDto } from './dto/fcm-token.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { UsersService } from './users.service';

@ApiTags('users')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get('me')
  @ApiOperation({ summary: 'Lay ho so cua chinh minh (tu tao neu dang nhap lan dau)' })
  getMe(@CurrentUser() user: AuthUser) {
    return this.usersService.getProfile(user);
  }

  @Patch('me')
  @ApiOperation({ summary: 'Cap nhat ho so (ten hien thi / avatar)' })
  updateMe(@CurrentUser() user: AuthUser, @Body() dto: UpdateUserDto) {
    return this.usersService.updateProfile(user.uid, dto);
  }

  @Post('me/fcm-tokens')
  @ApiOperation({ summary: 'Dang ky FCM token cua thiet bi' })
  async addFcmToken(@CurrentUser() user: AuthUser, @Body() dto: FcmTokenDto) {
    await this.usersService.addFcmToken(user.uid, dto.token);
    return { message: 'Da dang ky nhan thong bao.', data: { token: dto.token } };
  }

  /**
   * Cron endpoint — cron-job.org goi MOI TOI (khuyen nghi 13:00 UTC = 20:00 gio VN)
   * voi header `x-cron-secret`. Khong can user Firebase.
   */
  @Post('streak-reminders/send')
  @Public()
  @UseGuards(CronSecretGuard)
  @ApiSecurity('cron')
  @ApiHeader({
    name: CRON_SECRET_HEADER,
    required: true,
    description: 'Secret của cron (env CRON_SECRET) — chỉ cron-job.org biết',
  })
  @ApiOperation({
    summary: '[Cron] Gửi push nhắc giữ streak cho user chưa đăng bài hôm nay',
    description:
      'Chọn user có streak > 0 và lần tăng streak cuối là HÔM QUA (hôm nay chưa đăng → nửa đêm mất streak). ' +
      'Push DATA-ONLY `type=STREAK_REMINDER` để app tự hiện thông báo theo toggle "Streak reminders". ' +
      'Trả `{candidates, notified}`.',
  })
  @ApiResponse({ status: 200, description: 'Đã gửi — data = {candidates, notified}' })
  @ApiResponse({ status: 401, description: 'Sai cron secret' })
  @ApiResponse({ status: 503, description: 'Chưa cấu hình CRON_SECRET' })
  sendStreakReminders() {
    return this.usersService.sendStreakReminders();
  }

  @Delete('me/fcm-tokens/:token')
  @ApiOperation({ summary: 'Go FCM token (khi logout / het han)' })
  async removeFcmToken(@CurrentUser() user: AuthUser, @Param('token') token: string) {
    await this.usersService.removeFcmToken(user.uid, token);
    return { message: 'Da go token.', data: { token } };
  }

  @Get(':uid')
  @ApiOperation({ summary: 'Xem ho so cong khai cua nguoi dung khac' })
  getPublicProfile(@Param('uid') uid: string) {
    return this.usersService.getPublicProfile(uid);
  }
}
