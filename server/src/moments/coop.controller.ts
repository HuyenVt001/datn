import { Body, Controller, Get, Param, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { AcceptCoopInviteDto, CreateCoopInviteDto } from './dto/coop.dto';
import { CoopService } from './coop.service';

@ApiTags('moments-coop')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('moments/coop')
export class CoopController {
  constructor(private readonly coopService: CoopService) {}

  @Post()
  @ApiOperation({ summary: 'Gui loi moi chup chung (kem nua anh cua minh tu /upload)' })
  async createInvite(@CurrentUser() user: AuthUser, @Body() dto: CreateCoopInviteDto) {
    const invite = await this.coopService.createInvite(user.uid, dto);
    return { message: 'Da gui loi moi chup chung.', data: invite };
  }

  @Get('pending')
  @ApiOperation({ summary: 'Danh sach loi moi chup chung dang cho minh tra loi' })
  listPending(@CurrentUser() user: AuthUser) {
    return this.coopService.listPending(user.uid);
  }

  @Post(':id/accept')
  @ApiOperation({
    summary: 'Chap nhan: nop nua anh con lai -> server ghep 2 anh thanh 1 moment chung',
  })
  async accept(
    @CurrentUser() user: AuthUser,
    @Param('id') inviteId: string,
    @Body() dto: AcceptCoopInviteDto,
  ) {
    const moment = await this.coopService.accept(user.uid, inviteId, dto);
    return { message: 'Da ghep anh va dang khoanh khac chung!', data: moment };
  }

  @Post(':id/decline')
  @ApiOperation({ summary: 'Tu choi loi moi chup chung' })
  async decline(@CurrentUser() user: AuthUser, @Param('id') inviteId: string) {
    await this.coopService.decline(user.uid, inviteId);
    return { message: 'Da tu choi loi moi.', data: { inviteId } };
  }
}
