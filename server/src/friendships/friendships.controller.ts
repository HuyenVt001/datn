import { Body, Controller, Delete, Get, Param, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { ConnectFriendDto } from './dto/connect-friend.dto';
import { FriendshipsService } from './friendships.service';

@ApiTags('friendships')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('friendships')
export class FriendshipsController {
  constructor(private readonly friendshipsService: FriendshipsService) {}

  @Get()
  @ApiOperation({ summary: 'Friend list (with friend streak)' })
  list(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.listMyFriends(user.uid);
  }

  @Get('invite-link')
  @ApiOperation({
    summary: 'Get your own friend invite link (valid 30 days, a new code is generated on expiry)',
  })
  getInviteLink(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.getInviteLink(user.uid);
  }

  @Get('invite-info/:code')
  @ApiOperation({
    summary: 'Inviter info from an invite code (app shows a confirm dialog before connecting)',
  })
  getInviteInfo(@Param('code') code: string) {
    return this.friendshipsService.getInviteInfo(code);
  }

  @Post('connect')
  @ApiOperation({
    summary:
      'Send a friend request via invite code — the LINK OWNER must accept (if both invite each other, they become friends immediately)',
  })
  async connect(@CurrentUser() user: AuthUser, @Body() dto: ConnectFriendDto) {
    const friendship = await this.friendshipsService.connect(user.uid, dto.inviteCode);
    const message =
      friendship.status === 'ACCEPTED'
        ? 'You\'re now friends.'
        : 'Friend request sent — waiting for acceptance.';
    return { message, data: friendship };
  }

  @Get('requests')
  @ApiOperation({ summary: 'Friend requests waiting for you (link owner) to accept' })
  listRequests(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.listRequests(user.uid);
  }

  @Post('requests/:requesterUid/accept')
  @ApiOperation({ summary: 'Accept a friend request (re-checks the 20-friend limit on both sides)' })
  async acceptRequest(@CurrentUser() user: AuthUser, @Param('requesterUid') requesterUid: string) {
    const friendship = await this.friendshipsService.acceptRequest(user.uid, requesterUid);
    return { message: 'Friend request accepted.', data: friendship };
  }

  @Post('requests/:requesterUid/decline')
  @ApiOperation({ summary: 'Decline a friend request (silently removed, sender can invite again)' })
  async declineRequest(@CurrentUser() user: AuthUser, @Param('requesterUid') requesterUid: string) {
    await this.friendshipsService.declineRequest(user.uid, requesterUid);
    return { message: 'Friend request declined.', data: { requesterUid } };
  }

  @Delete(':friendUid')
  @ApiOperation({ summary: 'Remove a friend' })
  async remove(@CurrentUser() user: AuthUser, @Param('friendUid') friendUid: string) {
    await this.friendshipsService.removeFriend(user.uid, friendUid);
    return { message: 'Friend removed.', data: { friendUid } };
  }
}
