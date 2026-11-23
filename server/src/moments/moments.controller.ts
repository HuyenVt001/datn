import { Body, Controller, Delete, Get, Param, Post, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { PaginationDto } from '../common/dto/pagination.dto';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { CreateMomentDto } from './dto/create-moment.dto';
import { ReactDto } from './dto/react.dto';
import { MomentsService } from './moments.service';

@ApiTags('moments')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('moments')
export class MomentsController {
  constructor(private readonly momentsService: MomentsService) {}

  @Post()
  @ApiOperation({
    summary: 'Post a moment (photo/video). Increases personal streak + notifies friends via FCM',
    description:
      "Response = the new moment. If the server has AI on, the moment is a PHOTO and the user hasn't finished today's AI quest:" +
      'adds field `aiQuest: { result: MATCHED | NOT_MATCHED | SKIPPED, score?, questContent? }` —' +
      'MATCHED = AI quest auto-completed + 30 Astrite credited; NOT_MATCHED = post still published, quest not done;' +
      'SKIPPED = AI error/timeout, posting unaffected. (2026-08-15)',
  })
  async create(@CurrentUser() user: AuthUser, @Body() dto: CreateMomentDto) {
    const moment = await this.momentsService.create(user, dto);
    return { message: 'Posted.', data: moment };
  }

  @Get('feed')
  @ApiOperation({ summary: "Feed: your moments + friends' (newest first, paginated)" })
  getFeed(@CurrentUser() user: AuthUser, @Query() pagination: PaginationDto) {
    return this.momentsService.getFeed(user.uid, pagination);
  }

  @Get('mine')
  @ApiOperation({ summary: 'Your own moments (profile: calendar + total count)' })
  listMine(@CurrentUser() user: AuthUser, @Query() pagination: PaginationDto) {
    return this.momentsService.listMine(user.uid, pagination);
  }

  @Get('user/:uid')
  @ApiOperation({ summary: "A user's moments — only friends (or yourself) can view" })
  listOfUser(
    @CurrentUser() user: AuthUser,
    @Param('uid') uid: string,
    @Query() pagination: PaginationDto,
  ) {
    return this.momentsService.listOfUser(user.uid, uid, pagination);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete a moment — ONLY the owner can' })
  async remove(@CurrentUser() user: AuthUser, @Param('id') momentId: string) {
    await this.momentsService.delete(momentId, user.uid);
    return { message: 'Post deleted.', data: { momentId } };
  }

  @Post(':id/seen')
  @ApiOperation({ summary: 'Mark a moment as seen (called when the user scrolls past)' })
  async markSeen(@CurrentUser() user: AuthUser, @Param('id') momentId: string) {
    await this.momentsService.markSeen(momentId, user.uid);
    return { message: 'Marked as seen.', data: { momentId } };
  }

  @Post(':id/reactions')
  @ApiOperation({
    summary: 'React to a moment with an emoji (updates friend streak with the owner)',
  })
  react(@CurrentUser() user: AuthUser, @Param('id') momentId: string, @Body() dto: ReactDto) {
    return this.momentsService.react(momentId, user.uid, dto.emojiType);
  }

  @Get(':id/reactions')
  @ApiOperation({ summary: "A moment's reactions (only people who can see the post)" })
  listReactions(@CurrentUser() user: AuthUser, @Param('id') momentId: string) {
    return this.momentsService.listReactions(momentId, user.uid);
  }
}
