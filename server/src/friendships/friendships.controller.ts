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
  @ApiOperation({ summary: 'Lay link moi ket ban cua chinh minh' })
  getInviteLink(@CurrentUser() user: AuthUser) {
    return this.friendshipsService.getInviteLink(user.uid);
  }

  @Post('connect')
  @ApiOperation({ summary: 'Ket ban qua ma moi (kiem tra gioi han 20 ca 2 phia)' })
  async connect(@CurrentUser() user: AuthUser, @Body() dto: ConnectFriendDto) {
    const friendship = await this.friendshipsService.connect(user.uid, dto.inviteCode);
    return { message: 'Ket ban thanh cong.', data: friendship };
  }

  @Delete(':friendUid')
  @ApiOperation({ summary: 'Xoa ban be' })
  async remove(@CurrentUser() user: AuthUser, @Param('friendUid') friendUid: string) {
    await this.friendshipsService.removeFriend(user.uid, friendUid);
    return { message: 'Da xoa ban be.', data: { friendUid } };
  }
}
