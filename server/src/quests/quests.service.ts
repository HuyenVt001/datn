import { Injectable, Logger } from '@nestjs/common';
import { AstriteService } from '../astrite/astrite.service';
import { dateKey, QUEST_DAILY_ASTRITE, STREAK_MILESTONES } from '../common/constants';
import { FramesRepository } from '../frames/frames.repository';
import { FramesService } from '../frames/frames.service';
import { QuestType, TodayQuestsResult } from './entities/quest.entity';
import { QuestsRepository } from './quests.repository';

/**
 * Daily Quest — phien ban KHONG AI (quyet dinh 2026-07-13):
 * - 2 quest co dinh/ngay (LOGIN + POST_MOMENT), sinh LAZY khi co request dau tien trong ngay.
 * - Hoan thanh tu dong (khong nop anh, khong xac minh).
 * - Thuong: xong 2/2 quest trong ngay -> +60 Astrite (doi tu "mo khung ngau nhien"
 *   ngay 2026-08-05 khi co he thong gacha — khung gio mo qua quay);
 *   dat moc streak ca nhan 3/7/14/30 -> mo khung cua moc do (frame co field `milestone`).
 */
@Injectable()
export class QuestsService {
  private readonly logger = new Logger(QuestsService.name);

  constructor(
    private readonly repo: QuestsRepository,
    private readonly astrite: AstriteService,
    private readonly framesService: FramesService,
    private readonly framesRepo: FramesRepository,
  ) {}

  /**
   * Quest cua hom nay + trang thai cua user hien tai.
   * Goi endpoint nay = user da mo app -> TU hoan thanh quest LOGIN.
   */
  async getTodayQuests(uid: string): Promise<TodayQuestsResult> {
    const date = this.todayKey();
    const daily = await this.repo.ensureDailyQuests(date);

    await this.completeQuest(uid, date, 'LOGIN');

    const statuses = await this.repo.getUserQuests(date, uid);
    const rewardAstrite = await this.repo.getDailyReward(date, uid);

    return {
      quests: daily.map((quest) => ({
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

  /** Thong ke cho admin dashboard: so luot hoan thanh quest hom nay. */
  async countCompletionsToday(): Promise<number> {
    return this.repo.countCompletionsByDate(this.todayKey());
  }

  /** Hoan thanh quest (idempotent); lan dau hoan thanh thi xet thuong 2/2. */
  private async completeQuest(uid: string, date: string, type: QuestType): Promise<void> {
    const isFirstTime = await this.repo.completeUserQuest(date, uid, type);
    if (isFirstTime) {
      await this.maybeGiveDailyReward(uid, date);
    }
  }

  /** Xong CA 2 quest trong ngay -> thuong QUEST_DAILY_ASTRITE (moi ngay toi da 1 lan). */
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
  private todayKey(): string {
    return dateKey();
  }
}
