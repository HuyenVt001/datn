import { Injectable, Logger } from '@nestjs/common';
import { AiService } from '../ai/ai.service';
import { AstriteService } from '../astrite/astrite.service';
import {
  AI_QUEST_AVOID_RECENT,
  AI_QUEST_CLASSES,
  AI_QUEST_CONTENT_MAX,
  AiQuestClass,
  dateKey,
  QUEST_AI_ASTRITE,
  QUEST_DAILY_ASTRITE,
  STREAK_MILESTONES,
} from '../common/constants';
import { FramesRepository } from '../frames/frames.repository';
import { FramesService } from '../frames/frames.service';
import { Moment } from '../moments/entities/moment.entity';
import { PaginatedResult, PaginationDto } from '../common/dto/pagination.dto';
import { pickFallbackQuest } from './entities/ai-quest-templates';
import { AiVerificationDailyStats, AiVerificationRecord } from './entities/ai-verification.entity';
import {
  AiQuestGenerationResult,
  AiQuestResult,
  DailyQuest,
  QuestType,
  TodayQuestsResult,
} from './entities/quest.entity';
import { QuestsRepository } from './quests.repository';

/**
 * Daily Quest — 3 quest/ngay (nang tu 2 len 3 ngay 2026-08-15, QUEST_AI_PLAN.md):
 * - 2 quest CO DINH (LOGIN + POST_MOMENT), sinh LAZY khi co request dau tien trong
 *   ngay, hoan thanh tu dong. Xong 2/2 -> +60 Astrite (logic GIU NGUYEN tung dong).
 * - 1 quest AI (AI_CHALLENGE): noi dung lay tu BO MAU 72 cau (ai-quest-templates.ts,
 *   chon theo seed ngay, tranh lap vat the 3 ngay gan nhat) — user chot 2026-08-16
 *   BO LLM sinh quest, AI chi lam nhiem vu XAC MINH ANH. Hoan thanh khi anh user
 *   DANG LEN FEED duoc model AI xac minh co chua `targetClass` -> +30 Astrite RIENG.
 *   AI hong o bat ky khau nao cung KHONG pha chuc nang dang chay: verify hong thi
 *   SKIPPED, thieu env thi khong verify (quest van hien, chi khong tick duoc).
 *   Duong LLM (`generateAiQuests` + `source:'LLM'`) giu lai lam diem cam san.
 * - Moc streak ca nhan 3/7/14/30 -> mo khung cua moc do (frame co field `milestone`).
 */
@Injectable()
export class QuestsService {
  private readonly logger = new Logger(QuestsService.name);

  constructor(
    private readonly repo: QuestsRepository,
    private readonly astrite: AstriteService,
    private readonly framesService: FramesService,
    private readonly framesRepo: FramesRepository,
    private readonly ai: AiService,
  ) {}

  /**
   * Quest cua hom nay + trang thai cua user hien tai.
   * Goi endpoint nay = user da mo app -> TU hoan thanh quest LOGIN.
   * Quest AI: chua co thi tao NGAY tu bo mau (khong goi LLM trong request cua user);
   * AI tat (thieu env) -> chi tra 2 quest nhu truoc.
   */
  async getTodayQuests(uid: string): Promise<TodayQuestsResult> {
    const date = this.todayKey();
    const daily = await this.repo.ensureDailyQuests(date);

    // Quest AI hong (Firestore loi doc AI, ...) KHONG duoc lam hong 2 quest co dinh
    const aiQuest = await this.ensureAiQuest(date, false).catch((e) => {
      this.logger.warn(`Khong lay duoc quest AI ngay ${date}: ${(e as Error).message}`);
      return null;
    });
    const all = aiQuest ? [...daily, aiQuest] : daily;

    await this.completeQuest(uid, date, 'LOGIN');

    const statuses = await this.repo.getUserQuests(date, uid);
    const rewardAstrite = await this.repo.getDailyReward(date, uid);

    return {
      quests: all.map((quest) => ({
        ...quest,
        completed: statuses.has(quest.type),
        completedAt: statuses.get(quest.type)?.completedAt,
      })),
      rewardAstrite,
    };
  }

  /**
   * Hook tu MomentsService.create: hoan thanh quest POST_MOMENT + xet thuong moc streak.
   * personalStreak = gia tri SAU khi da tang boi registerActivityForStreak.
   */
  async registerMomentPosted(uid: string, personalStreak: number): Promise<void> {
    const date = this.todayKey();
    await this.repo.ensureDailyQuests(date);
    await this.completeQuest(uid, date, 'POST_MOMENT');
    await this.rewardStreakMilestone(uid, personalStreak);
  }

  /**
   * Hook thu 2 tu MomentsService.create (sau registerMomentPosted): AI xac minh
   * anh vua dang so voi quest AI hom nay.
   *
   * Tra ve undefined khi KHONG co gi de xac minh (AI tat / khong phai PHOTO /
   * hom nay chua co quest AI / user da xong quest AI) -> response dang bai khong
   * co field `aiQuest`, y het truoc day. Da so request roi vao nhanh nay nen
   * POST /moments khong cham them.
   *
   * KHONG BAO GIO throw: Space loi/timeout -> SKIPPED. Loi Firestore o buoc
   * thuong -> log + SKIPPED (caller van bat them 1 lop nua cho chac).
   */
  async verifyAiQuest(uid: string, moment: Moment): Promise<AiQuestResult | undefined> {
    if (!this.ai.enabled || moment.contentType !== 'PHOTO') {
      return undefined;
    }
    const date = this.todayKey();
    const quest = await this.repo.getAiQuest(date);
    if (!quest?.targetClass) {
      return undefined;
    }
    if (await this.repo.hasCompletedQuest(date, uid, 'AI_CHALLENGE')) {
      return undefined; // da xong hom nay — khong ton 1 lan goi Space nua
    }

    const startedAt = Date.now();
    const imageUrl = AiService.toVerifyImageUrl(moment.mediaUrl);
    let verdict: Awaited<ReturnType<AiService['verify']>>;
    try {
      verdict = await this.ai.verify(imageUrl, quest.targetClass);
    } catch (e) {
      const message = (e as Error).message;
      this.logger.warn(`AI verify SKIPPED (moment ${moment.momentId}): ${message}`);
      void this.logVerification({
        uid,
        momentId: moment.momentId,
        mediaUrl: imageUrl,
        date,
        targetClass: quest.targetClass,
        outcome: 'SKIPPED',
        roundTripMs: Date.now() - startedAt,
        error: message.slice(0, 200),
      });
      return { result: 'SKIPPED', questContent: quest.content };
    }

    const outcome = verdict.matched ? 'MATCHED' : 'NOT_MATCHED';
    void this.logVerification({
      uid,
      momentId: moment.momentId,
      mediaUrl: imageUrl,
      date,
      targetClass: quest.targetClass,
      outcome,
      score: verdict.score,
      threshold: verdict.threshold,
      scores: verdict.scores,
      modelVersion: verdict.modelVersion,
      latencyMs: verdict.latencyMs,
      roundTripMs: Date.now() - startedAt,
    });

    if (!verdict.matched) {
      return { result: 'NOT_MATCHED', score: verdict.score, questContent: quest.content };
    }

    try {
      await this.completeAiQuest(uid, date, {
        momentId: moment.momentId,
        aiScore: verdict.score,
        modelVersion: verdict.modelVersion,
      });
    } catch (e) {
      // Anh DA khop nhung ghi hoan thanh/cong tien loi -> bao SKIPPED de user
      // dang bai khac thu lai (quest chua tick nen lan sau van verify + thuong)
      this.logger.warn(`Khop quest AI nhung khong thuong duoc cho ${uid}: ${(e as Error).message}`);
      return { result: 'SKIPPED', score: verdict.score, questContent: quest.content };
    }
    return { result: 'MATCHED', score: verdict.score, questContent: quest.content };
  }

  /**
   * Cron endpoint POST /quests/ai/generate: dam bao quest AI cua HOM NAY va NGAY MAI
   * ton tai. Idempotent: ngay nao da co quest thi giu nguyen (khong ghi de).
   *
   * ⚠️ 2026-08-16: cron nay thanh TUY CHON — bo LLM nen quest sinh tu bo mau, va
   * `getTodayQuests` da tu tao khi user dau tien mo app trong ngay. Giu endpoint vi:
   * (1) sinh truoc ngay mai de biet quest demo, (2) diem cam lai LLM sau nay
   * (`ensureAiQuest(date, true)` goi AiService.generate neu ENABLE_LLM bat o service).
   */
  async generateAiQuests(): Promise<AiQuestGenerationResult[]> {
    if (!this.ai.enabled) {
      return [];
    }
    const today = this.todayKey();
    const tomorrow = this.todayKey(new Date(Date.now() + 24 * 60 * 60 * 1000));
    const results: AiQuestGenerationResult[] = [];
    for (const date of [today, tomorrow]) {
      const existing = await this.repo.getAiQuest(date);
      if (existing) {
        results.push({ date, quest: existing, created: false });
        continue;
      }
      const quest = await this.ensureAiQuest(date, true);
      if (quest) {
        results.push({ date, quest, created: true });
      }
    }
    return results;
  }

  /** Thong ke cho admin dashboard: so luot hoan thanh quest hom nay. */
  async countCompletionsToday(): Promise<number> {
    return this.repo.countCompletionsByDate(this.todayKey());
  }

  /** Toi da so log verify doc 1 lan cho trang admin (phan trang trong bo nho). */
  private static readonly AI_VERIFICATION_SCAN_LIMIT = 500;

  /**
   * Log AI verify cho trang admin (moi nhat truoc), loc outcome/date/uid trong bo nho.
   * Nguon so lieu "accuracy thuc te" + de calibrate lai nguong (QUEST_AI_PLAN muc 8.1, 15.3).
   */
  async listAiVerifications(
    pagination: PaginationDto,
    filter: { outcome?: AiVerificationRecord['outcome']; date?: string; uid?: string } = {},
  ): Promise<PaginatedResult<AiVerificationRecord>> {
    let items = await this.repo.listAiVerifications(QuestsService.AI_VERIFICATION_SCAN_LIMIT);
    if (filter.outcome) {
      items = items.filter((v) => v.outcome === filter.outcome);
    }
    if (filter.date) {
      items = items.filter((v) => v.date === filter.date);
    }
    if (filter.uid) {
      items = items.filter((v) => v.uid === filter.uid);
    }
    const { page, limit } = pagination;
    const start = (page - 1) * limit;
    return { items: items.slice(start, start + limit), page, limit, total: items.length };
  }

  /** Thong ke verify AI hom nay cho dashboard admin (tong / khop / khong khop / bo qua). */
  async getAiVerificationStatsToday(): Promise<AiVerificationDailyStats> {
    const date = this.todayKey();
    const items = await this.repo.listAiVerificationsByDate(date);
    return {
      date,
      total: items.length,
      matched: items.filter((v) => v.outcome === 'MATCHED').length,
      notMatched: items.filter((v) => v.outcome === 'NOT_MATCHED').length,
      skipped: items.filter((v) => v.outcome === 'SKIPPED').length,
    };
  }

  /**
   * Quest AI cua `date`: da co -> tra ve; chua co & AI bat -> sinh (LLM neu
   * `useLlm`, khong thi FALLBACK) roi tao ATOMIC (thua luong khac -> dung doc cua
   * luong do). AI tat -> null. LLM tra ket qua khong hop le -> fallback + warn.
   */
  private async ensureAiQuest(date: string, useLlm: boolean): Promise<DailyQuest | null> {
    if (!this.ai.enabled) {
      return null;
    }
    const existing = await this.repo.getAiQuest(date);
    if (existing) {
      return existing;
    }

    const avoid = await this.repo.getRecentAiTargets(date, AI_QUEST_AVOID_RECENT);
    let picked = pickFallbackQuest(date, avoid);
    let source: DailyQuest['source'] = 'FALLBACK';

    if (useLlm) {
      try {
        const raw = await this.ai.generate(avoid);
        const validated = this.validateGenerated(raw, avoid);
        if (validated) {
          picked = validated;
          source = 'LLM';
        } else {
          this.logger.warn(
            `LLM tra quest khong hop le cho ${date} (${JSON.stringify(raw).slice(0, 120)}) — dung fallback`,
          );
        }
      } catch (e) {
        this.logger.warn(`LLM sinh quest ${date} loi: ${(e as Error).message} — dung fallback`);
      }
    }

    const { quest, created } = await this.repo.createAiQuestIfAbsent({
      type: 'AI_CHALLENGE',
      content: picked.content,
      releaseDate: date,
      targetClass: picked.targetClass,
      source,
      generatedAt: new Date().toISOString(),
    });
    if (created) {
      this.logger.log(`Quest AI ${date} [${source}] ${picked.targetClass}: "${picked.content}"`);
    }
    return quest;
  }

  /**
   * Validate ket qua LLM (KHONG tin AI service): targetClass phai thuoc AI_QUEST_CLASSES va
   * (neu con lua chon) khong nam trong avoid; content khong rong, <= max ky tu.
   */
  private validateGenerated(
    raw: { targetClass?: unknown; content?: unknown } | null | undefined,
    avoid: readonly string[],
  ): { targetClass: AiQuestClass; content: string } | null {
    if (!raw || typeof raw.targetClass !== 'string' || typeof raw.content !== 'string') {
      return null;
    }
    const targetClass = raw.targetClass.trim().toLowerCase();
    if (!(AI_QUEST_CLASSES as readonly string[]).includes(targetClass)) {
      return null;
    }
    const remaining = AI_QUEST_CLASSES.filter((c) => !avoid.includes(c));
    if (remaining.length > 0 && avoid.includes(targetClass)) {
      return null;
    }
    const content = raw.content.replace(/\s+/g, ' ').trim();
    if (content.length < 5 || content.length > AI_QUEST_CONTENT_MAX) {
      return null;
    }
    return { targetClass: targetClass as AiQuestClass, content };
  }

  /**
   * Hoan thanh quest AI + cong QUEST_AI_ASTRITE — mirror pattern chong double-credit
   * cua maybeGiveDailyReward: completeUserQuest atomic (isFirstTime), cong tien fail
   * -> xoa doc vua tao de lan dang sau thu lai. KHONG dung maybeGiveDailyReward —
   * logic 60 Astrite cua 2/2 giu nguyen.
   */
  private async completeAiQuest(
    uid: string,
    date: string,
    meta: { momentId: string; aiScore: number; modelVersion: string },
  ): Promise<void> {
    const isFirstTime = await this.repo.completeUserQuest(date, uid, 'AI_CHALLENGE', meta);
    if (!isFirstTime) {
      return; // 2 bai dang cung luc cung khop -> chi 1 lan +30
    }
    try {
      await this.astrite.credit(uid, QUEST_AI_ASTRITE, 'AI_QUEST_REWARD', date);
    } catch (e) {
      await this.repo
        .deleteUserQuest(date, uid, 'AI_CHALLENGE')
        .catch(() => this.logger.warn(`Khong tra lai duoc quest AI ngay ${date} cho ${uid}`));
      throw e;
    }
    this.logger.log(`Thuong ${QUEST_AI_ASTRITE} Astrite cho ${uid} (quest AI ngay ${date})`);
  }

  /** Ghi log verify best-effort (nhu adminLogs) — khong bao gio throw. */
  private async logVerification(
    entry: Omit<Parameters<QuestsRepository['addAiVerification']>[0], 'createdAt'>,
  ): Promise<void> {
    try {
      await this.repo.addAiVerification({ ...entry, createdAt: new Date().toISOString() });
    } catch (e) {
      this.logger.warn(`Khong ghi duoc aiVerifications: ${(e as Error).message}`);
    }
  }

  /** Hoan thanh quest (idempotent); lan dau hoan thanh thi xet thuong 2/2. */
  private async completeQuest(uid: string, date: string, type: QuestType): Promise<void> {
    const isFirstTime = await this.repo.completeUserQuest(date, uid, type);
    if (isFirstTime) {
      await this.maybeGiveDailyReward(uid, date);
    }
  }

  /** Xong CA 2 quest CO DINH trong ngay -> thuong QUEST_DAILY_ASTRITE (moi ngay toi da 1 lan). */
  private async maybeGiveDailyReward(uid: string, date: string): Promise<void> {
    const statuses = await this.repo.getUserQuests(date, uid);
    if (!statuses.has('LOGIN') || !statuses.has('POST_MOMENT')) {
      return;
    }

    // Claim ATOMIC truoc khi thuong — 2 quest hoan thanh dong thoi (LOGIN qua
    // GET /quests/today + POST_MOMENT qua dang bai) thi chi 1 luong duoc thuong.
    if (!(await this.repo.tryClaimDailyReward(date, uid))) {
      return; // hom nay da xet thuong roi
    }

    try {
      // refId = ngay: doi chieu duoc dong so cai voi doc claim cua ngay do
      await this.astrite.credit(uid, QUEST_DAILY_ASTRITE, 'QUEST_REWARD', date);
    } catch (e) {
      // Cong tien FAIL -> tra lai claim de lan goi sau thu lai duoc (khong tra
      // thi doc claim astrite:null nam do mai = user mat thuong ngay do).
      await this.repo
        .deleteDailyReward(date, uid)
        .catch(() => this.logger.warn(`Khong tra lai duoc claim thuong ngay ${date} cho ${uid}`));
      throw e;
    }

    // Tu day tro di TUYET DOI khong duoc xoa claim: tien da vao vi roi, tra lai
    // claim = lan goi sau cong them 60 nua. Ghi so hien thi that bai thi chi
    // mat con so tren UI (doc claim doc ra null = "da xet thuong"), khong mat tien.
    this.logger.log(`Thuong ${QUEST_DAILY_ASTRITE} Astrite cho ${uid} (2/2 quest ngay ${date})`);
    await this.repo
      .setDailyRewardAstrite(date, uid, QUEST_DAILY_ASTRITE)
      .catch(() => this.logger.warn(`Da cong Astrite nhung khong ghi duoc moc thuong ${date}`));
  }

  /** Dat dung moc streak (3/7/14/30) -> mo khung cua moc (unlockFrame arrayUnion nen idempotent). */
  private async rewardStreakMilestone(uid: string, streak: number): Promise<void> {
    if (!STREAK_MILESTONES.includes(streak)) {
      return;
    }
    const frames = await this.framesRepo.list();
    const frame = frames.find(
      (f) => f.unlockType === 'STREAK_MILESTONE' && f.unlockValue === streak,
    );
    if (!frame) {
      return; // admin chua tao khung cho moc nay
    }
    await this.framesService.unlockForUser(uid, frame.frameId);
    this.logger.log(`Mo khung moc streak ${streak} (${frame.frameId}) cho ${uid}`);
  }

  /** YYYY-MM-DD — dung dateKey CHUNG voi personal streak (common/constants). */
  private todayKey(date?: Date): string {
    return dateKey(date);
  }
}
