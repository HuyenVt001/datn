import { AiService } from '../ai/ai.service';
import { AstriteService } from '../astrite/astrite.service';
import { QUEST_AI_ASTRITE, QUEST_DAILY_ASTRITE } from '../common/constants';
import { FramesRepository } from '../frames/frames.repository';
import { FramesService } from '../frames/frames.service';
import { Moment } from '../moments/entities/moment.entity';
import { DailyQuest, QuestType, UserQuest } from './entities/quest.entity';
import { QuestsRepository } from './quests.repository';
import { QuestsService } from './quests.service';

describe('QuestsService', () => {
  let service: QuestsService;
  let repo: jest.Mocked<QuestsRepository>;
  let astrite: jest.Mocked<AstriteService>;
  let framesService: jest.Mocked<FramesService>;
  let framesRepo: jest.Mocked<FramesRepository>;
  let ai: jest.Mocked<AiService> & { enabled: boolean };

  const today = new Date().toISOString().slice(0, 10);

  const aiQuest: DailyQuest = {
    questId: `${today}_AI_CHALLENGE`,
    type: 'AI_CHALLENGE',
    content: 'Take a photo of a cup on your desk',
    releaseDate: today,
    targetClass: 'cup',
    source: 'FALLBACK',
    generatedAt: `${today}T00:05:00.000Z`,
  };

  const photo: Moment = {
    momentId: 'm1',
    userId: 'me',
    contentType: 'PHOTO',
    mediaUrl: 'https://res.cloudinary.com/demo/image/upload/v1/snapget/abc.jpg',
    postTime: `${today}T08:00:00.000Z`,
  };

  const verdict = (matched: boolean, score = 0.9) => ({
    matched,
    score,
    threshold: 0.35,
    scores: { cup: score },
    modelVersion: 'v0',
    latencyMs: 60,
  });

  const dailyQuests = [
    {
      questId: `${today}_LOGIN`,
      type: 'LOGIN',
      content: 'Login to Snapget',
      releaseDate: today,
    },
    {
      questId: `${today}_POST_MOMENT`,
      type: 'POST_MOMENT',
      content: 'Post any photo or GIF',
      releaseDate: today,
    },
  ];

  const userQuest = (type: QuestType): UserQuest => ({
    questId: `${today}_${type}`,
    userId: 'me',
    type,
    releaseDate: today,
    status: 'COMPLETED',
    completedAt: `${today}T00:00:00.000Z`,
  });

  beforeEach(() => {
    repo = {
      ensureDailyQuests: jest.fn().mockResolvedValue(dailyQuests),
      getUserQuests: jest.fn().mockResolvedValue(new Map()),
      completeUserQuest: jest.fn().mockResolvedValue(true),
      getDailyReward: jest.fn().mockResolvedValue(undefined),
      tryClaimDailyReward: jest.fn().mockResolvedValue(true),
      setDailyRewardAstrite: jest.fn().mockResolvedValue(undefined),
      deleteDailyReward: jest.fn().mockResolvedValue(undefined),
      countCompletionsByDate: jest.fn().mockResolvedValue(0),
      // AI quest (2026-08-15)
      getAiQuest: jest.fn().mockResolvedValue(null),
      createAiQuestIfAbsent: jest.fn().mockImplementation((q) =>
        Promise.resolve({
          quest: { questId: `${q.releaseDate}_AI_CHALLENGE`, ...q },
          created: true,
        }),
      ),
      getRecentAiTargets: jest.fn().mockResolvedValue([]),
      hasCompletedQuest: jest.fn().mockResolvedValue(false),
      deleteUserQuest: jest.fn().mockResolvedValue(undefined),
      addAiVerification: jest.fn().mockResolvedValue(undefined),
      listAiVerifications: jest.fn().mockResolvedValue([]),
      listAiVerificationsByDate: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<QuestsRepository>;
    astrite = {
      credit: jest.fn().mockResolvedValue(QUEST_DAILY_ASTRITE),
    } as unknown as jest.Mocked<AstriteService>;
    framesService = {
      unlockForUser: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<FramesService>;
    framesRepo = {
      list: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FramesRepository>;

    ai = {
      enabled: false, // mac dinh AI TAT — moi test AI tu bat
      verify: jest.fn(),
      generate: jest.fn(),
    } as unknown as jest.Mocked<AiService> & { enabled: boolean };

    service = new QuestsService(repo, astrite, framesService, framesRepo, ai);
  });

  describe('getTodayQuests', () => {
    it("lazily creates the day's quests + auto-completes the LOGIN quest", async () => {
      repo.getUserQuests.mockResolvedValue(new Map([['LOGIN', userQuest('LOGIN')]]));

      const result = await service.getTodayQuests('me');

      expect(repo.ensureDailyQuests).toHaveBeenCalledWith(today);
      expect(repo.completeUserQuest).toHaveBeenCalledWith(today, 'me', 'LOGIN');
      expect(result.quests).toHaveLength(2);
      expect(result.quests.find((q) => q.type === 'LOGIN')?.completed).toBe(true);
      expect(result.quests.find((q) => q.type === 'POST_MOMENT')?.completed).toBe(false);
    });
  });

  describe('registerMomentPosted', () => {
    it('completes the POST_MOMENT quest when posting', async () => {
      await service.registerMomentPosted('me', 1);
      expect(repo.completeUserQuest).toHaveBeenCalledWith(today, 'me', 'POST_MOMENT');
    });

    it('2/2 quests done -> credits 60 Astrite (no longer unlocks a frame)', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );

      await service.registerMomentPosted('me', 1);

      expect(astrite.credit).toHaveBeenCalledWith('me', QUEST_DAILY_ASTRITE, 'QUEST_REWARD', today);
      expect(repo.setDailyRewardAstrite).toHaveBeenCalledWith(today, 'me', QUEST_DAILY_ASTRITE);
      expect(framesService.unlockForUser).not.toHaveBeenCalled();
    });

    it('already rewarded today -> no second reward (atomic claim returns false)', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      repo.tryClaimDailyReward.mockResolvedValue(false); // luong khac da claim / da thuong

      await service.registerMomentPosted('me', 1);

      expect(astrite.credit).not.toHaveBeenCalled();
      expect(repo.setDailyRewardAstrite).not.toHaveBeenCalled();
    });

    it('Astrite credit FAILS -> releases the claim so it can be retried', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      astrite.credit.mockRejectedValue(new Error('network down'));

      await expect(service.registerMomentPosted('me', 1)).rejects.toThrow('network down');
      expect(repo.deleteDailyReward).toHaveBeenCalledWith(today, 'me');
    });

    it('Astrite credited but writing the reward marker fails -> KEEPS the claim (no double credit)', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      repo.setDailyRewardAstrite.mockRejectedValue(new Error('write error'));

      await service.registerMomentPosted('me', 1);

      // Tien da vao vi — xoa claim la lan goi sau cong them 60 nua
      expect(repo.deleteDailyReward).not.toHaveBeenCalled();
    });

    it('quest already completed (isFirstTime=false) -> reward not re-evaluated', async () => {
      repo.completeUserQuest.mockResolvedValue(false);

      await service.registerMomentPosted('me', 1);

      expect(repo.getUserQuests).not.toHaveBeenCalled();
    });

    it('reaching streak 7 -> unlocks the milestone-7 frame', async () => {
      framesRepo.list.mockResolvedValue([
        {
          frameId: 'f7',
          frameName: 'Moc 7',
          unlockType: 'STREAK_MILESTONE',
          unlockValue: 7,
          milestone: 7,
          createdAt: '',
        },
      ]);

      await service.registerMomentPosted('me', 7);

      expect(framesService.unlockForUser).toHaveBeenCalledWith('me', 'f7');
    });

    it('streak not a milestone (5) -> no milestone frame', async () => {
      framesRepo.list.mockResolvedValue([
        {
          frameId: 'f7',
          frameName: 'Moc 7',
          unlockType: 'STREAK_MILESTONE',
          unlockValue: 7,
          milestone: 7,
          createdAt: '',
        },
      ]);
      repo.completeUserQuest.mockResolvedValue(false); // khong xet thuong 2/2

      await service.registerMomentPosted('me', 5);

      expect(framesService.unlockForUser).not.toHaveBeenCalled();
    });
  });

  // ==== Quest AI (2026-08-15 — QUEST_AI_PLAN.md) ====

  describe('getTodayQuests + AI quest', () => {
    it('AI off -> still exactly 2 quests, AI repo untouched', async () => {
      const result = await service.getTodayQuests('me');
      expect(result.quests).toHaveLength(2);
      expect(repo.getAiQuest).not.toHaveBeenCalled();
      expect(repo.createAiQuestIfAbsent).not.toHaveBeenCalled();
    });

    it("AI on, cron hasn't generated -> creates a FALLBACK quest right away (no LLM call) and returns 3 quests", async () => {
      ai.enabled = true;

      const result = await service.getTodayQuests('me');

      expect(ai.generate).not.toHaveBeenCalled();
      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'AI_CHALLENGE', source: 'FALLBACK', releaseDate: today }),
      );
      expect(result.quests).toHaveLength(3);
      const q = result.quests.find((x) => x.type === 'AI_CHALLENGE');
      expect(q?.targetClass).toBeDefined();
      expect(q?.content.startsWith('Take a photo of')).toBe(true);
      expect(q?.completed).toBe(false);
    });

    it('AI on, AI quest exists -> reused, nothing new created; completed=true when done', async () => {
      ai.enabled = true;
      repo.getAiQuest.mockResolvedValue(aiQuest);
      repo.getUserQuests.mockResolvedValue(
        new Map([['AI_CHALLENGE', { ...userQuest('AI_CHALLENGE'), aiScore: 0.9 }]]),
      );

      const result = await service.getTodayQuests('me');

      expect(repo.createAiQuestIfAbsent).not.toHaveBeenCalled();
      expect(result.quests.find((x) => x.type === 'AI_CHALLENGE')?.completed).toBe(true);
    });

    it('fallback avoids objects from the last 3 days', async () => {
      ai.enabled = true;
      repo.getRecentAiTargets.mockResolvedValue(['cup', 'bottle', 'book']);

      await service.getTodayQuests('me');

      const created = repo.createAiQuestIfAbsent.mock.calls[0][0];
      expect(['cup', 'bottle', 'book']).not.toContain(created.targetClass);
    });

    it('reading the AI quest fails -> still returns the 2 fixed quests (GET /quests/today not broken)', async () => {
      ai.enabled = true;
      repo.getAiQuest.mockRejectedValue(new Error('firestore down'));

      const result = await service.getTodayQuests('me');

      expect(result.quests).toHaveLength(2);
    });
  });

  describe('verifyAiQuest', () => {
    beforeEach(() => {
      ai.enabled = true;
      repo.getAiQuest.mockResolvedValue(aiQuest);
    });

    it('AI off -> undefined, Space not called', async () => {
      ai.enabled = false;
      expect(await service.verifyAiQuest('me', photo)).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('VIDEO -> undefined (only photos are verified)', async () => {
      expect(await service.verifyAiQuest('me', { ...photo, contentType: 'VIDEO' })).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('no AI quest today -> undefined', async () => {
      repo.getAiQuest.mockResolvedValue(null);
      expect(await service.verifyAiQuest('me', photo)).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it("user already finished today's AI quest -> undefined, no Space call wasted", async () => {
      repo.hasCompletedQuest.mockResolvedValue(true);
      expect(await service.verifyAiQuest('me', photo)).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('match -> MATCHED, completes quest with meta + credits 30 Astrite + logs verification', async () => {
      ai.verify.mockResolvedValue(verdict(true, 0.87));

      const result = await service.verifyAiQuest('me', photo);

      // Gui URL Cloudinary DA thu nho (canh dai <= 640, giu ti le) cho AI service
      expect(ai.verify).toHaveBeenCalledWith(
        'https://res.cloudinary.com/demo/image/upload/w_640,h_640,c_limit,f_jpg,q_auto/v1/snapget/abc.jpg',
        ['cup', 'bottle'], // quest 'cup' gop nhom cup | bottle (2026-11-27)
      );
      expect(result).toEqual({ result: 'MATCHED', score: 0.87, questContent: aiQuest.content });
      expect(repo.completeUserQuest).toHaveBeenCalledWith(today, 'me', 'AI_CHALLENGE', {
        momentId: 'm1',
        aiScore: 0.87,
        modelVersion: 'v0',
      });
      expect(astrite.credit).toHaveBeenCalledWith('me', QUEST_AI_ASTRITE, 'AI_QUEST_REWARD', today);
      // Log verify (best-effort, fire-and-forget) — cho microtask chay
      await new Promise((r) => setImmediate(r));
      expect(repo.addAiVerification).toHaveBeenCalledWith(
        expect.objectContaining({
          uid: 'me',
          momentId: 'm1',
          outcome: 'MATCHED',
          score: 0.87,
          // Luu URL DA transform de trang admin hien thumbnail khong can join posts
          mediaUrl:
            'https://res.cloudinary.com/demo/image/upload/w_640,h_640,c_limit,f_jpg,q_auto/v1/snapget/abc.jpg',
        }),
      );
    });

    it('no match -> NOT_MATCHED, quest not ticked, no credit, still logged', async () => {
      ai.verify.mockResolvedValue(verdict(false, 0.12));

      const result = await service.verifyAiQuest('me', photo);

      expect(result).toEqual({ result: 'NOT_MATCHED', score: 0.12, questContent: aiQuest.content });
      expect(repo.completeUserQuest).not.toHaveBeenCalled();
      expect(astrite.credit).not.toHaveBeenCalled();
      await new Promise((r) => setImmediate(r));
      expect(repo.addAiVerification).toHaveBeenCalledWith(
        expect.objectContaining({ outcome: 'NOT_MATCHED' }),
      );
    });

    it('Space error/timeout -> SKIPPED, no throw, no credit', async () => {
      ai.verify.mockRejectedValue(new Error('AI Space /verify timeout after 3000ms'));

      const result = await service.verifyAiQuest('me', photo);

      expect(result).toEqual({ result: 'SKIPPED', questContent: aiQuest.content });
      expect(repo.completeUserQuest).not.toHaveBeenCalled();
      expect(astrite.credit).not.toHaveBeenCalled();
      await new Promise((r) => setImmediate(r));
      expect(repo.addAiVerification).toHaveBeenCalledWith(
        expect.objectContaining({ outcome: 'SKIPPED', error: expect.stringContaining('timeout') }),
      );
    });

    it('2 simultaneous matching posts -> only one +30 (atomic completeUserQuest returns false)', async () => {
      ai.verify.mockResolvedValue(verdict(true));
      repo.completeUserQuest.mockResolvedValue(false);

      const result = await service.verifyAiQuest('me', photo);

      expect(result?.result).toBe('MATCHED');
      expect(astrite.credit).not.toHaveBeenCalled();
    });

    it('crediting 30 Astrite FAILS -> deletes the new quest doc (next post retries) + returns SKIPPED', async () => {
      ai.verify.mockResolvedValue(verdict(true));
      astrite.credit.mockRejectedValue(new Error('network down'));

      const result = await service.verifyAiQuest('me', photo);

      expect(repo.deleteUserQuest).toHaveBeenCalledWith(today, 'me', 'AI_CHALLENGE');
      expect(result?.result).toBe('SKIPPED');
    });

    it('verification log error -> result unaffected', async () => {
      ai.verify.mockResolvedValue(verdict(true));
      repo.addAiVerification.mockRejectedValue(new Error('log fail'));

      const result = await service.verifyAiQuest('me', photo);
      await new Promise((r) => setImmediate(r));

      expect(result?.result).toBe('MATCHED');
    });

    it('non-Cloudinary URL -> sent to Space as-is', async () => {
      ai.verify.mockResolvedValue(verdict(false));
      await service.verifyAiQuest('me', { ...photo, mediaUrl: 'https://cdn.example.com/a.jpg' });
      expect(ai.verify).toHaveBeenCalledWith('https://cdn.example.com/a.jpg', ['cup', 'bottle']);
    });

    it('grouped quest matched by another member (bottle) -> MATCHED, logs matchedClass + targetClasses', async () => {
      ai.verify.mockResolvedValue({ ...verdict(true, 0.81), matchedClass: 'bottle' });

      const result = await service.verifyAiQuest('me', photo);
      await new Promise((r) => setImmediate(r));

      expect(result?.result).toBe('MATCHED');
      expect(repo.addAiVerification).toHaveBeenCalledWith(
        expect.objectContaining({
          targetClass: 'cup',
          targetClasses: ['cup', 'bottle'],
          matchedClass: 'bottle',
        }),
      );
    });

    it('person quest -> asks the AI service for the "person" class (>= 2 people model)', async () => {
      repo.getAiQuest.mockResolvedValue({ ...aiQuest, targetClass: 'person' });
      ai.verify.mockResolvedValue(verdict(false, 0.2));

      await service.verifyAiQuest('me', photo);

      expect(ai.verify).toHaveBeenCalledWith(expect.any(String), ['person']);
    });

    it('legacy quest doc created before 2026-11-27 (clock) -> still verified as a single class', async () => {
      repo.getAiQuest.mockResolvedValue({
        ...aiQuest,
        targetClass: 'clock' as unknown as DailyQuest['targetClass'],
      });
      ai.verify.mockResolvedValue(verdict(true));

      const result = await service.verifyAiQuest('me', photo);

      expect(ai.verify).toHaveBeenCalledWith(expect.any(String), ['clock']);
      expect(result?.result).toBe('MATCHED');
    });
  });

  describe('generateAiQuests (cron)', () => {
    const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

    it('AI off -> empty array, no calls', async () => {
      expect(await service.generateAiQuests()).toEqual([]);
      expect(ai.generate).not.toHaveBeenCalled();
    });

    it('valid LLM result -> creates LLM-source quests for today + tomorrow', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValue({
        targetClass: 'laptop',
        content: 'Take a photo of your laptop',
      });

      const results = await service.generateAiQuests();

      expect(results.map((r) => r.date)).toEqual([today, tomorrow]);
      expect(results.every((r) => r.created)).toBe(true);
      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({
          releaseDate: today,
          source: 'LLM',
          targetClass: 'laptop',
          content: 'Take a photo of your laptop',
        }),
      );
      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({ releaseDate: tomorrow, source: 'LLM' }),
      );
    });

    it('LLM returns a targetClass outside the quest list -> fallback template', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValue({ targetClass: 'cat', content: 'Take a photo of a cat' });

      await service.generateAiQuests();

      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({ source: 'FALLBACK' }),
      );
    });

    it('LLM returns a targetClass in avoid (other options exist) -> fallback', async () => {
      ai.enabled = true;
      repo.getRecentAiTargets.mockResolvedValue(['cup']);
      ai.generate.mockResolvedValue({ targetClass: 'cup', content: 'Take a photo of a cup' });

      await service.generateAiQuests();

      const created = repo.createAiQuestIfAbsent.mock.calls[0][0];
      expect(created.source).toBe('FALLBACK');
      expect(created.targetClass).not.toBe('cup');
    });

    it('LLM returns empty / too long content -> fallback', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValueOnce({ targetClass: 'cup', content: '   ' });
      ai.generate.mockResolvedValueOnce({ targetClass: 'cup', content: 'x'.repeat(200) });

      await service.generateAiQuests();

      expect(repo.createAiQuestIfAbsent.mock.calls.every((c) => c[0].source === 'FALLBACK')).toBe(
        true,
      );
    });

    it('LLM error/timeout -> fallback, cron still returns results', async () => {
      ai.enabled = true;
      ai.generate.mockRejectedValue(new Error('AI Space /generate timeout'));

      const results = await service.generateAiQuests();

      expect(results).toHaveLength(2);
      expect(results[0].quest.source).toBe('FALLBACK');
    });

    it('idempotent: a day with a quest -> unchanged, created=false, no LLM call for that day', async () => {
      ai.enabled = true;
      repo.getAiQuest.mockImplementation((date) =>
        Promise.resolve(date === today ? aiQuest : null),
      );
      ai.generate.mockResolvedValue({
        targetClass: 'chair',
        content: 'Take a photo of the chair you are sitting on',
      });

      const results = await service.generateAiQuests();

      expect(results[0]).toEqual({ date: today, quest: aiQuest, created: false });
      expect(results[1].created).toBe(true);
      expect(ai.generate).toHaveBeenCalledTimes(1);
    });

    it("loses the create race -> uses the winner's quest (no overwrite)", async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValue({
        targetClass: 'chair',
        content: 'Take a photo of the chair you are sitting on',
      });
      repo.createAiQuestIfAbsent.mockResolvedValue({ quest: aiQuest, created: false });

      const results = await service.generateAiQuests();

      expect(results[0].quest).toEqual(aiQuest);
    });
  });

  describe('listAiVerifications / getAiVerificationStatsToday (admin)', () => {
    const rec = (
      id: string,
      outcome: 'MATCHED' | 'NOT_MATCHED' | 'SKIPPED',
      date = today,
      uid = 'me',
    ) => ({
      id,
      uid,
      momentId: `m-${id}`,
      date,
      targetClass: 'cup' as const,
      outcome,
      createdAt: `${date}T0${id}:00:00.000Z`,
    });

    it('in-memory pagination + outcome/date/uid filters', async () => {
      repo.listAiVerifications.mockResolvedValue([
        rec('1', 'MATCHED'),
        rec('2', 'NOT_MATCHED'),
        rec('3', 'MATCHED', '2026-01-01'),
        rec('4', 'MATCHED', today, 'other'),
      ]);

      const all = await service.listAiVerifications({ page: 1, limit: 2 });
      expect(all.total).toBe(4);
      expect(all.items.map((v) => v.id)).toEqual(['1', '2']);

      const matched = await service.listAiVerifications(
        { page: 1, limit: 10 },
        { outcome: 'MATCHED' },
      );
      expect(matched.items.map((v) => v.id)).toEqual(['1', '3', '4']);

      const todayMine = await service.listAiVerifications(
        { page: 1, limit: 10 },
        { date: today, uid: 'me' },
      );
      expect(todayMine.items.map((v) => v.id)).toEqual(['1', '2']);
    });

    it("today's stats: total / matched / not matched / skipped", async () => {
      repo.listAiVerificationsByDate.mockResolvedValue([
        rec('1', 'MATCHED'),
        rec('2', 'MATCHED'),
        rec('3', 'NOT_MATCHED'),
        rec('4', 'SKIPPED'),
      ]);

      const stats = await service.getAiVerificationStatsToday();

      expect(repo.listAiVerificationsByDate).toHaveBeenCalledWith(today);
      expect(stats).toEqual({ date: today, total: 4, matched: 2, notMatched: 1, skipped: 1 });
    });
  });
});
