import { Injectable, Logger } from '@nestjs/common';
import { dateKey, STREAK_MILESTONES } from '../common/constants';
import { Frame } from '../frames/entities/frame.entity';
import { FramesRepository } from '../frames/frames.repository';
import { FramesService } from '../frames/frames.service';
import { UsersRepository } from '../users/users.repository';
import { QuestType, TodayQuestsResult } from './entities/quest.entity';
import { QuestsRepository } from './quests.repository';

/**
 * Daily Quest — phien ban KHONG AI (quyet dinh 2026-07-13):
 * - 2 quest co dinh/ngay (LOGIN + POST_MOMENT), sinh LAZY khi co request dau tien trong ngay.
 * - Hoan thanh tu dong (khong nop anh, khong xac minh).
 * - Thuong: xong 2/2 quest trong ngay -> mo ngau nhien 1 khung thuong (khung KHONG gan moc streak);
 *   dat moc streak ca nhan 3/7/14/30 -> mo khung cua moc do (frame co field `milestone`).
 */
@Injectable()
export class QuestsService {
  private readonly logger = new Logger(QuestsService.name);

  constructor(
    private readonly repo: QuestsRepository,
    private readonly usersRepo: UsersRepository,
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
    const rewardFrameId = await this.repo.getDailyReward(date, uid);

    return {
      quests: daily.map((quest) => ({
        ...quest,
        completed: statuses.has(quest.type),
        completedAt: statuses.get(quest.type)?.completedAt,
      })),
      rewardFrameId,
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

  /** Xong CA 2 quest trong ngay -> thuong ngau nhien 1 khung chua so huu (moi ngay toi da 1 lan). */
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
      const frame = await this.pickRandomLockedFrame(uid);
      if (frame) {
        await this.framesService.unlockForUser(uid, frame.frameId);
        await this.repo.setDailyRewardFrame(date, uid, frame.frameId);
        this.logger.log(`Thuong khung ${frame.frameId} cho ${uid} (2/2 quest ngay ${date})`);
      }
    } catch (e) {
      // Thuong fail giua chung -> TRA LAI claim de lan goi sau thu lai duoc
      // (khong tra thi claim doc frameId:null nam do mai = user mat thuong ngay do)
      await this.repo
        .deleteDailyReward(date, uid)
        .catch(() => this.logger.warn(`Khong tra lai duoc claim thuong ngay ${date} cho ${uid}`));
      throw e;
    }
  }

  /** Ung vien thuong ngau nhien: khung KHONG gan moc streak va user CHUA so huu. */
  private async pickRandomLockedFrame(uid: string): Promise<Frame | null> {
    const [frames, user] = await Promise.all([
      this.framesRepo.list(),
      this.usersRepo.findByUid(uid),
    ]);
    const unlocked = new Set(user?.unlockedFrames ?? []);
    const candidates = frames.filter((f) => !f.milestone && !unlocked.has(f.frameId));
    if (candidates.length === 0) {
      return null;
    }
    return candidates[Math.floor(Math.random() * candidates.length)];
  }

  /** Dat dung moc streak (3/7/14/30) -> mo khung cua moc (unlockFrame arrayUnion nen idempotent). */
  private async rewardStreakMilestone(uid: string, streak: number): Promise<void> {
    if (!STREAK_MILESTONES.includes(streak)) {
      return;
    }
    const frames = await this.framesRepo.list();
    const frame = frames.find((f) => f.milestone === streak);
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
