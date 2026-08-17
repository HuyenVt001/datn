import { Module } from '@nestjs/common';
import { AiService } from './ai.service';

/**
 * Module AI (2026-08-15 — QUEST_AI_PLAN.md): HTTP client goi AI service
 * (FastAPI + ONNX, code o `ml/ai-service/`, deploy tren Google Cloud Run).
 * Khong controller, khong repository — chi export AiService cho quests dung
 * (xac minh anh quest). Thieu env -> AiService.enabled=false, tat em.
 */
@Module({
  providers: [AiService],
  exports: [AiService],
})
export class AiModule {}
