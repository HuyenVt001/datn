import { Body, Controller, Get, Param, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { CreateCoopInviteDto, SubmitCoopMediaDto } from './dto/coop.dto';
import { CoopService } from './coop.service';

@ApiTags('moments-coop')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('moments/coop')
export class CoopController {
  constructor(private readonly coopService: CoopService) {}

  @Post()
  @ApiOperation({ summary: 'Send a co-op capture invite (no photo, valid for 5 minutes)' })
  async createInvite(@CurrentUser() user: AuthUser, @Body() dto: CreateCoopInviteDto) {
    const invite = await this.coopService.createInvite(user.uid, dto);
    return { message: 'Co-op invite sent.', data: invite };
  }

  @Get('pending')
  @ApiOperation({ summary: 'Co-op invites waiting for your reply' })
  listPending(@CurrentUser() user: AuthUser) {
    return this.coopService.listPending(user.uid);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Invite details (both sides poll status on the co-op capture screen)' })
  getInvite(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    return this.coopService.getInvite(user.uid, inviteId);
  }

  @Post(':id/accept')
  @ApiOperation({ summary: 'Accept invite -> ACCEPTED, both enter the co-op capture screen' })
  async accept(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    const invite = await this.coopService.accept(user.uid, inviteId);
    return { message: 'Co-op invite accepted.', data: invite };
  }

  @Post(':id/decline')
  @ApiOperation({ summary: 'Decline (invitee) / cancel (inviter) a pending invite' })
  async decline(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    await this.coopService.decline(user.uid, inviteId);
    return { message: 'Invite declined.', data: { inviteId } };
  }

  @Post(':id/media')
  @ApiOperation({
    summary: 'Submit your half photo (after ACCEPTED) — once both halves are in, the server merges -> mergedMediaUrl',
  })
  async submitMedia(
    @CurrentUser() user: AuthUser,
    @Param('id') inviteId: string,
    @Body() dto: SubmitCoopMediaDto,
  ) {
    const invite = await this.coopService.submitMedia(user.uid, inviteId, dto);
    return { message: 'Your half photo was received.', data: invite };
  }
}
