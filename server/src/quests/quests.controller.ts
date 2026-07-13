import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { QuestsService } from './quests.service';

@ApiTags('quests')
@ApiBearerAuth('firebase')
@UseGuards(FirebaseAuthGuard)
@Controller('quests')
export class QuestsController {
  constructor(private readonly questsService: QuestsService) {}

  @Get('today')
  @ApiOperation({
    summary:
      '2 quest co dinh cua hom nay + trang thai hoan thanh (lazy tao quest; goi endpoint nay tu hoan thanh quest LOGIN)',
  })
  today(@CurrentUser() user: AuthUser) {
    return this.questsService.getTodayQuests(user.uid);
  }
}
