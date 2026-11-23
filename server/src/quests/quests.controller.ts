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
      "Today's quests + completion status: 2 fixed quests (LOGIN, POST_MOMENT) + AI quest (AI_CHALLENGE, only when the server has AI on). Calling this endpoint completes LOGIN.",
    description:
      'The AI quest also has `targetClass` (1 of the 9 quest object classes — `AI_QUEST_CLASSES`) and `source` (LLM | FALLBACK).' +
      'Complete the AI quest by posting a photo moment containing the object — the server verifies it during POST /moments.' +
      '`rewardAstrite` is still the reward for completing 2/2 fixed quests (+60); the AI quest rewards a separate +30.',
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
    description: 'Cron secret (env CRON_SECRET) — known only to cron-job.org',
  })
  @ApiOperation({
    summary:
      '[Cron] (optional) Pre-generate AI quests for today + tomorrow from the 72-sentence template set',
    description:
      'Idempotent: a day that already has an AI quest returns it (`created:false`), never overwrites.' +
      'Generates tomorrow ahead so the cron can run at any hour. AI off (missing env) → returns an empty array.' +
      'Missing CRON_SECRET → 503; wrong secret → 401.',
  })
  @ApiResponse({
    status: 201,
    description: 'List of {date, quest, created} for today + tomorrow',
  })
  @ApiResponse({ status: 401, description: 'Invalid cron secret' })
  @ApiResponse({ status: 503, description: 'CRON_SECRET is not configured on the server' })
  async generateAiQuests() {
    const results = await this.questsService.generateAiQuests();
    const created = results.filter((r) => r.created).length;
    return {
      message:
        results.length === 0
          ? 'AI is off — no quests generated.'
          : `Generated ${created} new AI quests.`,
      data: results,
    };
  }
}
