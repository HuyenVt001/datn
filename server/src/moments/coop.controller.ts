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
  @ApiOperation({ summary: 'Gui loi moi chup chung (khong kem anh, hieu luc 5 phut)' })
  async createInvite(@CurrentUser() user: AuthUser, @Body() dto: CreateCoopInviteDto) {
    const invite = await this.coopService.createInvite(user.uid, dto);
    return { message: 'Da gui loi moi chup chung.', data: invite };
  }

  @Get('pending')
  @ApiOperation({ summary: 'Danh sach loi moi chup chung dang cho minh tra loi' })
  listPending(@CurrentUser() user: AuthUser) {
    return this.coopService.listPending(user.uid);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Chi tiet loi moi (2 ben poll trang thai o man chup coop)' })
  getInvite(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    return this.coopService.getInvite(user.uid, inviteId);
  }

  @Post(':id/accept')
  @ApiOperation({ summary: 'Chap nhan loi moi -> ACCEPTED, ca 2 vao man chup coop' })
  async accept(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    const invite = await this.coopService.accept(user.uid, inviteId);
    return { message: 'Da chap nhan loi moi chup chung.', data: invite };
  }

  @Post(':id/decline')
  @ApiOperation({ summary: 'Tu choi (nguoi nhan) / huy (nguoi moi) loi moi dang cho' })
  async decline(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    await this.coopService.decline(user.uid, inviteId);
    return { message: 'Da tu choi loi moi.', data: { inviteId } };
  }

  @Post(':id/media')
  @ApiOperation({
    summary: 'Nop nua anh cua minh (sau ACCEPTED) — du 2 nua server ghep -> mergedMediaUrl',
  })
  async submitMedia(
    @CurrentUser() user: AuthUser,
    @Param('id') inviteId: string,
    @Body() dto: SubmitCoopMediaDto,
  ) {
    const invite = await this.coopService.submitMedia(user.uid, inviteId, dto);
    return { message: 'Da nhan nua anh cua ban.', data: invite };
  }
}
