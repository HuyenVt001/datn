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
    summary: 'Send a 1-1 (receiverId) or group (groupId) message. 1-1 updates the friend streak',
  })
  send(@CurrentUser() user: AuthUser, @Body() dto: SendMessageDto) {
    return this.messagesService.send(user, dto);
  }

  @Get('conversations')
  @ApiOperation({ summary: '1-1 conversation list (latest message with each person)' })
  getConversations(@CurrentUser() user: AuthUser) {
    return this.messagesService.getConversations(user.uid);
  }

  @Get('with/:friendUid')
  @ApiOperation({ summary: '1-1 thread with a person (page 1 = newest messages)' })
  getThread(
    @CurrentUser() user: AuthUser,
    @Param('friendUid') friendUid: string,
    @Query() pagination: PaginationDto,
  ) {
    return this.messagesService.getThread(user.uid, friendUid, pagination);
  }

  @Post(':id/reactions')
  @ApiOperation({
    summary: 'React to a message (conversation members; same emoji again = remove)',
  })
  react(
    @CurrentUser() user: AuthUser,
    @Param('id') messageId: string,
    @Body() dto: ReactMessageDto,
  ) {
    return this.messagesService.react(user.uid, messageId, dto.emoji);
  }

  @Patch(':id/seen')
  @ApiOperation({ summary: 'Mark a message as seen (recipient only)' })
  async markSeen(@CurrentUser() user: AuthUser, @Param('id') messageId: string) {
    await this.messagesService.markSeen(user.uid, messageId);
    return { message: 'Marked as seen.', data: { messageId } };
  }

  @Post('groups')
  @ApiOperation({ summary: 'Create a group chat (max 20 members, creator joins automatically)' })
  async createGroup(@CurrentUser() user: AuthUser, @Body() dto: CreateGroupDto) {
    const group = await this.messagesService.createGroup(user.uid, dto);
    return { message: 'Group created.', data: group };
  }

  @Get('groups')
  @ApiOperation({ summary: 'Your group chats' })
  listMyGroups(@CurrentUser() user: AuthUser) {
    return this.messagesService.listMyGroups(user.uid);
  }

  @Get('groups/:groupId')
  @ApiOperation({ summary: 'Group thread (members only)' })
  getGroupThread(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Query() pagination: PaginationDto,
  ) {
    return this.messagesService.getGroupThread(user.uid, groupId, pagination);
  }

  @Get('groups/:groupId/detail')
  @ApiOperation({ summary: 'Group details + member profiles (members only)' })
  getGroupDetail(@CurrentUser() user: AuthUser, @Param('groupId') groupId: string) {
    return this.messagesService.getGroupDetail(user.uid, groupId);
  }

  @Patch('groups/:groupId')
  @ApiOperation({ summary: 'Rename / change group avatar (any member can)' })
  async updateGroup(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Body() dto: UpdateGroupDto,
  ) {
    const group = await this.messagesService.updateGroup(user.uid, groupId, dto);
    return { message: 'Group updated.', data: group };
  }

  @Post('groups/:groupId/members')
  @ApiOperation({ summary: 'Add members to a group (only your friends)' })
  async addMembers(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Body() dto: AddGroupMembersDto,
  ) {
    const group = await this.messagesService.addMembers(user.uid, groupId, dto);
    return { message: 'Members added to the group.', data: group };
  }

  @Delete('groups/:groupId/members/:memberUid')
  @ApiOperation({ summary: 'Remove a member from a group (group creator only)' })
  async removeMember(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Param('memberUid') memberUid: string,
  ) {
    const group = await this.messagesService.removeMember(user.uid, groupId, memberUid);
    return { message: 'Member removed from the group.', data: group };
  }

  @Post('groups/:groupId/leave')
  @ApiOperation({ summary: 'Leave a group (last member leaving -> group deleted)' })
  async leaveGroup(@CurrentUser() user: AuthUser, @Param('groupId') groupId: string) {
    await this.messagesService.leaveGroup(user.uid, groupId);
    return { message: 'You left the group.', data: { groupId } };
  }

  @Patch('groups/:groupId/mute')
  @ApiOperation({ summary: 'Mute/unmute group notifications for yourself' })
  async muteGroup(
    @CurrentUser() user: AuthUser,
    @Param('groupId') groupId: string,
    @Body() dto: MuteGroupDto,
  ) {
    const group = await this.messagesService.setGroupMuted(user.uid, groupId, dto.muted);
    return {
      message: dto.muted ? 'Group notifications muted.' : 'Group notifications unmuted.',
      data: group,
    };
  }
}
