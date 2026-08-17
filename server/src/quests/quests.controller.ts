import { Controller, Get, Post, UseGuards } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiHeader,
  ApiOperation,
  ApiResponse,
  ApiSecurity,
  ApiTags,
} from '@nestjs/swagger';
import { AuthUser, CurrentUser } from '../common/decorators/current-user.decorator';
import { Public } from '../common/decorators/public.decorator';
import { CRON_SECRET_HEADER, CronSecretGuard } from '../common/guards/cron-secret.guard';
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
      'Quest hôm nay + trạng thái hoàn thành: 2 quest cố định (LOGIN, POST_MOMENT) + quest AI (AI_CHALLENGE, chỉ khi server bật AI). Gọi endpoint này tự hoàn thành LOGIN.',
    description:
      'Quest AI có thêm `targetClass` (1 trong 9 lớp vật thể ra đề — `AI_QUEST_CLASSES`) và `source` (LLM | FALLBACK). ' +
      'Hoàn thành quest AI bằng cách đăng moment ảnh có chứa vật thể — server tự xác minh lúc POST /moments. ' +
      '`rewardAstrite` vẫn là thưởng của mốc 2/2 quest cố định (+60); quest AI thưởng riêng +30.',
  })
  today(@CurrentUser() user: AuthUser) {
    return this.questsService.getTodayQuests(user.uid);
  }

  /**
   * Cron endpoint — cron-job.org gọi mỗi ngày (khuyến nghị 00:05 UTC) với header
   * `x-cron-secret`. Không cần user Firebase. Idempotent: quest đã có thì giữ nguyên.
   */
  @Post('ai/generate')
  @Public()
  @UseGuards(CronSecretGuard)
  @ApiSecurity('cron')
  @ApiHeader({
    name: CRON_SECRET_HEADER,
    required: true,
    description: 'Secret của cron (env CRON_SECRET) — chỉ cron-job.org biết',
  })
  @ApiOperation({
    summary: '[Cron] (tuỳ chọn) Sinh trước quest AI cho hôm nay + ngày mai từ bộ mẫu 72 câu',
    description:
      'Idempotent: ngày đã có quest AI thì trả lại quest đó (`created:false`), không ghi đè. ' +
      'Sinh trước ngày mai để cron chạy giờ nào cũng được. AI tắt (thiếu env) → trả mảng rỗng. ' +
      'Thiếu CRON_SECRET → 503; sai secret → 401.',
  })
  @ApiResponse({
    status: 201,
    description: 'Danh sách {date, quest, created} cho hôm nay + ngày mai',
  })
  @ApiResponse({ status: 401, description: 'Sai cron secret' })
  @ApiResponse({ status: 503, description: 'Server chưa cấu hình CRON_SECRET' })
  async generateAiQuests() {
    const results = await this.questsService.generateAiQuests();
    const created = results.filter((r) => r.created).length;
    return {
      message:
        results.length === 0
          ? 'AI dang tat — khong sinh quest.'
          : `Da sinh ${created} quest AI moi.`,
      data: results,
    };
  }
}
