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
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { PaginationDto } from '../common/dto/pagination.dto';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { AddGroupMembersDto } from './dto/add-group-members.dto';
import { CreateGroupDto } from './dto/create-group.dto';
import { MuteGroupDto } from './dto/mute-group.dto';
import { ReactMessageDto } from './dto/react-message.dto';
import { SendMessageDto } from './dto/send-message.dto';
import { UpdateGroupDto } from './dto/update-group.dto';
import { MessagesService } from './messages.service';

@ApiTags('messages')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('messages')
export class MessagesController {
  constructor(private readonly messagesService: MessagesService) {}

  @Post()
  @ApiOperation({
    summary: 'Gui tin nhan 1-1 (receiverId) hoac nhom (groupId). 1-1 cap nhat friend streak',
  })
  send(@CurrentUser() user: AuthUser, @Body() dto: SendMessageDto) {
    return this.messagesService.send(user, dto);
  }

  @Get('conversations')
  @ApiOperation({ summary: 'Danh sach hoi thoai 1-1 (tin moi nhat voi tung nguoi)' })
  getConversations(@CurrentUser() user: AuthUser) {
    return this.messagesService.getConversations(user.uid);
  }

  @Get('with/:friendUid')
  @ApiOperation({ summary: 'Thread 1-1 voi 1 nguoi (page 1 = tin moi nhat)' })
  getThread(
    @CurrentUser() user: AuthUser,
    @Param('friendUid') friendUid: string,
    @Query() pagination: PaginationDto,
  ) {
    return this.messagesService.getThread(user.uid, friendUid, pagination);
  }

  @Post(':id/reactions')
  @ApiOperation({
    summary: 'Tha reaction len tin nhan (nguoi trong hoi thoai; bam lai cung emoji = go)',
  })
  react(
    @CurrentUser() user: AuthUser,
    @Param('id') messageId: string,
    @Body() dto: ReactMessageDto,
  ) {
    return this.messagesService.react(user.uid, messageId, dto.emoji);
  }

  @Patch(':id/seen')
  @ApiOperation({ summary: 'Danh dau tin nhan da xem (chi nguoi nhan)' })
  async markSeen(@CurrentUser() user: AuthUser, @Param('id') messageId: string) {
    await this.messagesService.markSeen(user.uid, messageId);
    return { message: 'Da danh dau da xem.', data: { messageId } };
  }

  @Post('groups')
  @ApiOperation({ summary: 'Tao nhom chat (toi da 20 thanh vien, nguoi tao tu vao nhom)' })
  async createGroup(@CurrentUser() user: AuthUser, @Body() dto: CreateGroupDto) {
    const group = await this.messagesService.createGroup(user.uid, dto);
    return { message: 'Tao nhom thanh cong.', data: group };
  }

  @Get('groups')
  @ApiOperation({ summary: 'Danh sach nhom chat cua minh' })
  listMyGroups(@CurrentUser() user: AuthUser) {
    return this.messagesService.listMyGroups(user.uid);
  }

  @Get('groups/:groupId')
  @ApiOperation({ summary: 'Thread nhom (phai la thanh vien)' })
  getGroupThread(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Query() pagination: PaginationDto,
  ) {
    return this.messagesService.getGroupThread(user.uid, groupId, pagination);
  }

  @Get('groups/:groupId/detail')
  @ApiOperation({ summary: 'Chi tiet nhom + ho so thanh vien (phai la thanh vien)' })
  getGroupDetail(@CurrentUser() user: AuthUser, @Param('groupId') groupId: string) {
    return this.messagesService.getGroupDetail(user.uid, groupId);
  }

  @Patch('groups/:groupId')
  @ApiOperation({ summary: 'Doi ten / anh dai dien nhom (moi thanh vien deu doi duoc)' })
  async updateGroup(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Body() dto: UpdateGroupDto,
  ) {
    const group = await this.messagesService.updateGroup(user.uid, groupId, dto);
    return { message: 'Da cap nhat nhom.', data: group };
  }

  @Post('groups/:groupId/members')
  @ApiOperation({ summary: 'Them thanh vien vao nhom (chi them duoc ban be cua minh)' })
  async addMembers(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Body() dto: AddGroupMembersDto,
  ) {
    const group = await this.messagesService.addMembers(user.uid, groupId, dto);
    return { message: 'Da them thanh vien vao nhom.', data: group };
  }

  @Delete('groups/:groupId/members/:memberUid')
  @ApiOperation({ summary: 'Xoa thanh vien khoi nhom (chi nguoi tao nhom)' })
  async removeMember(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Param('memberUid') memberUid: string,
  ) {
    const group = await this.messagesService.removeMember(user.uid, groupId, memberUid);
    return { message: 'Da xoa thanh vien khoi nhom.', data: group };
  }

  @Post('groups/:groupId/leave')
  @ApiOperation({ summary: 'Roi nhom (nguoi cuoi cung roi -> xoa nhom)' })
  async leaveGroup(@CurrentUser() user: AuthUser, @Param('groupId') groupId: string) {
    await this.messagesService.leaveGroup(user.uid, groupId);
    return { message: 'Da roi nhom.', data: { groupId } };
  }

  @Patch('groups/:groupId/mute')
  @ApiOperation({ summary: 'Bat/tat thong bao nhom cho rieng minh' })
  async muteGroup(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Body() dto: MuteGroupDto,
  ) {
    const group = await this.messagesService.setGroupMuted(user.uid, groupId, dto.muted);
    return {
      message: dto.muted ? 'Da tat thong bao nhom.' : 'Da bat thong bao nhom.',
      data: group,
    };
  }
}
