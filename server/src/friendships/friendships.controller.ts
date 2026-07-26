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
  @ApiOperation({ summary: 'Danh sach ban be (kem friend streak)' })
  list(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.listMyFriends(user.uid);
  }

  @Get('invite-link')
  @ApiOperation({
    summary: 'Lay link moi ket ban cua chinh minh (hieu luc 30 ngay, het han tu sinh ma moi)',
  })
  getInviteLink(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.getInviteLink(user.uid);
  }

  @Get('invite-info/:code')
  @ApiOperation({
    summary: 'Thong tin nguoi moi tu ma moi (app hien dialog xac nhan truoc khi connect)',
  })
  getInviteInfo(@Param('code') code: string) {
    return this.friendshipsService.getInviteInfo(code);
  }

  @Post('connect')
  @ApiOperation({
    summary:
      'Gui loi moi ket ban qua ma moi — CHU LINK phai xac nhan moi thanh ban (2 ben cung moi nhau thi thanh ban luon)',
  })
  async connect(@CurrentUser() user: AuthUser, @Body() dto: ConnectFriendDto) {
    const friendship = await this.friendshipsService.connect(user.uid, dto.inviteCode);
    const message =
      friendship.status === 'ACCEPTED'
        ? 'Ket ban thanh cong.'
        : 'Da gui loi moi ket ban — cho xac nhan.';
    return { message, data: friendship };
  }

  @Get('requests')
  @ApiOperation({ summary: 'Danh sach loi moi ket ban dang cho minh (chu link) xac nhan' })
  listRequests(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.listRequests(user.uid);
  }

  @Post('requests/:requesterUid/accept')
  @ApiOperation({ summary: 'Chap nhan loi moi ket ban (kiem tra lai gioi han 20 ca 2 phia)' })
  async acceptRequest(@CurrentUser() user: AuthUser, @Param('requesterUid') requesterUid: string) {
    const friendship = await this.friendshipsService.acceptRequest(user.uid, requesterUid);
    return { message: 'Da chap nhan loi moi ket ban.', data: friendship };
  }

  @Post('requests/:requesterUid/decline')
  @ApiOperation({ summary: 'Tu choi loi moi ket ban (xoa im lang, nguoi gui co the moi lai)' })
  async declineRequest(@CurrentUser() user: AuthUser, @Param('requesterUid') requesterUid: string) {
    await this.friendshipsService.declineRequest(user.uid, requesterUid);
    return { message: 'Da tu choi loi moi ket ban.', data: { requesterUid } };
  }

  @Delete(':friendUid')
  @ApiOperation({ summary: 'Xoa ban be' })
  async remove(@CurrentUser() user: AuthUser, @Param('friendUid') friendUid: string) {
    await this.friendshipsService.removeFriend(user.uid, friendUid);
    return { message: 'Da xoa ban be.', data: { friendUid } };
  }
}
