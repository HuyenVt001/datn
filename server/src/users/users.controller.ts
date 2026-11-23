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
  @ApiOperation({ summary: 'Get your own profile (created on first sign-in)' })
  getMe(@CurrentUser() user: AuthUser) {
    return this.usersService.getProfile(user);
  }

  @Patch('me')
  @ApiOperation({ summary: 'Update profile (display name / avatar)' })
  updateMe(@CurrentUser() user: AuthUser, @Body() dto: UpdateUserDto) {
    return this.usersService.updateProfile(user.uid, dto);
  }

  @Post('me/fcm-tokens')
  @ApiOperation({ summary: 'Register the device FCM token' })
  async addFcmToken(@CurrentUser() user: AuthUser, @Body() dto: FcmTokenDto) {
    await this.usersService.addFcmToken(user.uid, dto.token);
    return { message: 'Registered for notifications.', data: { token: dto.token } };
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
    description: 'Cron secret (env CRON_SECRET) — known only to cron-job.org',
  })
  @ApiOperation({
    summary: "[Cron] Send streak reminder pushes to users who haven't posted today",
    description:
      'Selects users with streak > 0 whose last streak increase was YESTERDAY (no post today → streak lost at midnight).' +
      'DATA-ONLY push `type=STREAK_REMINDER` so the app shows the notification based on the "Streak reminders" toggle.' +
      'Returns `{candidates, notified}`.',
  })
  @ApiResponse({ status: 200, description: 'Sent — data = {candidates, notified}' })
  @ApiResponse({ status: 401, description: 'Invalid cron secret' })
  @ApiResponse({ status: 503, description: 'CRON_SECRET is not configured' })
  sendStreakReminders() {
    return this.usersService.sendStreakReminders();
  }

  @Delete('me/fcm-tokens/:token')
  @ApiOperation({ summary: 'Remove the FCM token (on logout / expiry)' })
  async removeFcmToken(@CurrentUser() user: AuthUser, @Param('token') token: string) {
    await this.usersService.removeFcmToken(user.uid, token);
    return { message: 'Token removed.', data: { token } };
  }

  @Get(':uid')
  @ApiOperation({ summary: "View another user's public profile" })
  getPublicProfile(@Param('uid') uid: string) {
    return this.usersService.getPublicProfile(uid);
  }
}
