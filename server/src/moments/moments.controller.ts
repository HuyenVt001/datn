import { Body, Controller, Get, Param, Post, Query, UseGuards } from '@nestjs/common';
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
  @ApiOperation({ summary: 'Dang moment (anh/video). Tang personal streak + bao ban be qua FCM' })
  async create(@CurrentUser() user: AuthUser, @Body() dto: CreateMomentDto) {
    const moment = await this.momentsService.create(user, dto);
    return { message: 'Dang bai thanh cong.', data: moment };
  }

  @Get('feed')
  @ApiOperation({ summary: 'Feed: moment cua minh + ban be (moi nhat truoc, co phan trang)' })
  getFeed(@CurrentUser() user: AuthUser, @Query() pagination: PaginationDto) {
    return this.momentsService.getFeed(user.uid, pagination);
  }

  @Get('mine')
  @ApiOperation({ summary: 'Moment cua chinh minh (profile: calendar + dem tong)' })
  listMine(@CurrentUser() user: AuthUser, @Query() pagination: PaginationDto) {
    return this.momentsService.listMine(user.uid, pagination);
  }

  @Get('user/:uid')
  @ApiOperation({ summary: 'Moment cua 1 user — chi ban be (hoac chinh minh) xem duoc' })
  listOfUser(
    @CurrentUser() user: AuthUser,
    @Param('uid') uid: string,
    @Query() pagination: PaginationDto,
  ) {
    return this.momentsService.listOfUser(user.uid, uid, pagination);
  }

  @Post(':id/seen')
  @ApiOperation({ summary: 'Danh dau da xem moment (he thong goi khi user luot qua)' })
  async markSeen(@CurrentUser() user: AuthUser, @Param('id') momentId: string) {
    await this.momentsService.markSeen(momentId, user.uid);
    return { message: 'Da danh dau da xem.', data: { momentId } };
  }

  @Post(':id/reactions')
  @ApiOperation({ summary: 'Tha emoji len moment (cap nhat friend streak voi chu bai)' })
  react(@CurrentUser() user: AuthUser, @Param('id') momentId: string, @Body() dto: ReactDto) {
    return this.momentsService.react(momentId, user.uid, dto.emojiType);
  }

  @Get(':id/reactions')
  @ApiOperation({ summary: 'Danh sach reaction cua moment' })
  listReactions(@Param('id') momentId: string) {
    return this.momentsService.listReactions(momentId);
  }
}
