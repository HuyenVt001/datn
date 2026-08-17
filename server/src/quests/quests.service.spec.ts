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
    content: 'Chụp một chiếc cốc trên bàn của bạn',
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
      content: 'Đăng nhập vào Snapget',
      releaseDate: today,
    },
    {
      questId: `${today}_POST_MOMENT`,
      type: 'POST_MOMENT',
      content: 'Đăng một ảnh hoặc video bất kỳ',
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
    it('lazy tao quest cua ngay + tu hoan thanh quest LOGIN', async () => {
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
    it('hoan thanh quest POST_MOMENT khi dang bai', async () => {
      await service.registerMomentPosted('me', 1);
      expect(repo.completeUserQuest).toHaveBeenCalledWith(today, 'me', 'POST_MOMENT');
    });

    it('xong 2/2 quest -> cong 60 Astrite (khong mo khung nua)', async () => {
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

    it('da thuong hom nay roi thi khong thuong lai (claim atomic tra ve false)', async () => {
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

    it('cong Astrite FAIL -> tra lai claim de lan sau thu lai duoc', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      astrite.credit.mockRejectedValue(new Error('mat mang'));

      await expect(service.registerMomentPosted('me', 1)).rejects.toThrow('mat mang');
      expect(repo.deleteDailyReward).toHaveBeenCalledWith(today, 'me');
    });

    it('cong Astrite XONG nhung ghi moc thuong fail -> GIU claim (khong cong 2 lan)', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      repo.setDailyRewardAstrite.mockRejectedValue(new Error('ghi loi'));

      await service.registerMomentPosted('me', 1);

      // Tien da vao vi — xoa claim la lan goi sau cong them 60 nua
      expect(repo.deleteDailyReward).not.toHaveBeenCalled();
    });

    it('quest da hoan thanh truoc do (isFirstTime=false) -> khong xet thuong lai', async () => {
      repo.completeUserQuest.mockResolvedValue(false);

      await service.registerMomentPosted('me', 1);

      expect(repo.getUserQuests).not.toHaveBeenCalled();
    });

    it('dat moc streak 7 -> mo khung cua moc 7', async () => {
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

    it('streak khong phai moc (5) -> khong mo khung moc', async () => {
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

  describe('getTodayQuests + quest AI', () => {
    it('AI tat -> van dung 2 quest, khong dong toi repo AI', async () => {
      const result = await service.getTodayQuests('me');
      expect(result.quests).toHaveLength(2);
      expect(repo.getAiQuest).not.toHaveBeenCalled();
      expect(repo.createAiQuestIfAbsent).not.toHaveBeenCalled();
    });

    it('AI bat, cron chua sinh -> tao quest FALLBACK ngay (khong goi LLM) va tra 3 quest', async () => {
      ai.enabled = true;

      const result = await service.getTodayQuests('me');

      expect(ai.generate).not.toHaveBeenCalled();
      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'AI_CHALLENGE', source: 'FALLBACK', releaseDate: today }),
      );
      expect(result.quests).toHaveLength(3);
      const q = result.quests.find((x) => x.type === 'AI_CHALLENGE');
      expect(q?.targetClass).toBeDefined();
      expect(q?.content.startsWith('Chụp')).toBe(true);
      expect(q?.completed).toBe(false);
    });

    it('AI bat, quest AI da co -> dung lai, khong tao moi; da xong thi completed=true', async () => {
      ai.enabled = true;
      repo.getAiQuest.mockResolvedValue(aiQuest);
      repo.getUserQuests.mockResolvedValue(
        new Map([['AI_CHALLENGE', { ...userQuest('AI_CHALLENGE'), aiScore: 0.9 }]]),
      );

      const result = await service.getTodayQuests('me');

      expect(repo.createAiQuestIfAbsent).not.toHaveBeenCalled();
      expect(result.quests.find((x) => x.type === 'AI_CHALLENGE')?.completed).toBe(true);
    });

    it('fallback tranh lap vat the 3 ngay gan nhat', async () => {
      ai.enabled = true;
      repo.getRecentAiTargets.mockResolvedValue(['cup', 'bottle', 'book']);

      await service.getTodayQuests('me');

      const created = repo.createAiQuestIfAbsent.mock.calls[0][0];
      expect(['cup', 'bottle', 'book']).not.toContain(created.targetClass);
    });

    it('doc quest AI loi -> van tra 2 quest co dinh (khong pha GET /quests/today)', async () => {
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

    it('AI tat -> undefined, khong goi Space', async () => {
      ai.enabled = false;
      expect(await service.verifyAiQuest('me', photo)).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('VIDEO -> undefined (chi verify anh)', async () => {
      expect(await service.verifyAiQuest('me', { ...photo, contentType: 'VIDEO' })).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('hom nay chua co quest AI -> undefined', async () => {
      repo.getAiQuest.mockResolvedValue(null);
      expect(await service.verifyAiQuest('me', photo)).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('user da xong quest AI hom nay -> undefined, khong ton 1 lan goi Space', async () => {
      repo.hasCompletedQuest.mockResolvedValue(true);
      expect(await service.verifyAiQuest('me', photo)).toBeUndefined();
      expect(ai.verify).not.toHaveBeenCalled();
    });

    it('khop -> MATCHED, hoan thanh quest kem meta + cong 30 Astrite + log verify', async () => {
      ai.verify.mockResolvedValue(verdict(true, 0.87));

      const result = await service.verifyAiQuest('me', photo);

      // Gui URL Cloudinary DA resize 224 cho Space
      expect(ai.verify).toHaveBeenCalledWith(
        'https://res.cloudinary.com/demo/image/upload/w_224,h_224,c_fill,f_jpg,q_auto/v1/snapget/abc.jpg',
        'cup',
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
          // Luu URL DA transform 224 de trang admin hien thumbnail khong can join posts
          mediaUrl:
            'https://res.cloudinary.com/demo/image/upload/w_224,h_224,c_fill,f_jpg,q_auto/v1/snapget/abc.jpg',
        }),
      );
    });

    it('khong khop -> NOT_MATCHED, quest chua tick, khong cong tien, van log', async () => {
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

    it('Space loi/timeout -> SKIPPED, khong throw, khong cong tien', async () => {
      ai.verify.mockRejectedValue(new Error('AI Space /verify timeout sau 3000ms'));

      const result = await service.verifyAiQuest('me', photo);

      expect(result).toEqual({ result: 'SKIPPED', questContent: aiQuest.content });
      expect(repo.completeUserQuest).not.toHaveBeenCalled();
      expect(astrite.credit).not.toHaveBeenCalled();
      await new Promise((r) => setImmediate(r));
      expect(repo.addAiVerification).toHaveBeenCalledWith(
        expect.objectContaining({ outcome: 'SKIPPED', error: expect.stringContaining('timeout') }),
      );
    });

    it('2 bai dang cung luc cung khop -> chi 1 lan +30 (completeUserQuest atomic tra false)', async () => {
      ai.verify.mockResolvedValue(verdict(true));
      repo.completeUserQuest.mockResolvedValue(false);

      const result = await service.verifyAiQuest('me', photo);

      expect(result?.result).toBe('MATCHED');
      expect(astrite.credit).not.toHaveBeenCalled();
    });

    it('cong 30 Astrite FAIL -> xoa doc quest vua tao (lan dang sau thu lai) + tra SKIPPED', async () => {
      ai.verify.mockResolvedValue(verdict(true));
      astrite.credit.mockRejectedValue(new Error('mat mang'));

      const result = await service.verifyAiQuest('me', photo);

      expect(repo.deleteUserQuest).toHaveBeenCalledWith(today, 'me', 'AI_CHALLENGE');
      expect(result?.result).toBe('SKIPPED');
    });

    it('log verify loi -> khong anh huong ket qua', async () => {
      ai.verify.mockResolvedValue(verdict(true));
      repo.addAiVerification.mockRejectedValue(new Error('log fail'));

      const result = await service.verifyAiQuest('me', photo);
      await new Promise((r) => setImmediate(r));

      expect(result?.result).toBe('MATCHED');
    });

    it('URL khong phai Cloudinary -> gui nguyen cho Space', async () => {
      ai.verify.mockResolvedValue(verdict(false));
      await service.verifyAiQuest('me', { ...photo, mediaUrl: 'https://cdn.example.com/a.jpg' });
      expect(ai.verify).toHaveBeenCalledWith('https://cdn.example.com/a.jpg', 'cup');
    });
  });

  describe('generateAiQuests (cron)', () => {
    const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

    it('AI tat -> mang rong, khong goi gi', async () => {
      expect(await service.generateAiQuests()).toEqual([]);
      expect(ai.generate).not.toHaveBeenCalled();
    });

    it('LLM tra hop le -> tao quest source LLM cho hom nay + ngay mai', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValue({
        targetClass: 'laptop',
        content: 'Chụp chiếc laptop của bạn',
      });

      const results = await service.generateAiQuests();

      expect(results.map((r) => r.date)).toEqual([today, tomorrow]);
      expect(results.every((r) => r.created)).toBe(true);
      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({
          releaseDate: today,
          source: 'LLM',
          targetClass: 'laptop',
          content: 'Chụp chiếc laptop của bạn',
        }),
      );
      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({ releaseDate: tomorrow, source: 'LLM' }),
      );
    });

    it('LLM tra targetClass ngoai danh sach ra de -> fallback template', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValue({ targetClass: 'cat', content: 'Chụp một chú mèo' });

      await service.generateAiQuests();

      expect(repo.createAiQuestIfAbsent).toHaveBeenCalledWith(
        expect.objectContaining({ source: 'FALLBACK' }),
      );
    });

    it('LLM tra targetClass nam trong avoid (con lua chon khac) -> fallback', async () => {
      ai.enabled = true;
      repo.getRecentAiTargets.mockResolvedValue(['cup']);
      ai.generate.mockResolvedValue({ targetClass: 'cup', content: 'Chụp một chiếc cốc' });

      await service.generateAiQuests();

      const created = repo.createAiQuestIfAbsent.mock.calls[0][0];
      expect(created.source).toBe('FALLBACK');
      expect(created.targetClass).not.toBe('cup');
    });

    it('LLM tra content rong / qua dai -> fallback', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValueOnce({ targetClass: 'cup', content: '   ' });
      ai.generate.mockResolvedValueOnce({ targetClass: 'cup', content: 'x'.repeat(200) });

      await service.generateAiQuests();

      expect(repo.createAiQuestIfAbsent.mock.calls.every((c) => c[0].source === 'FALLBACK')).toBe(
        true,
      );
    });

    it('LLM loi/timeout -> fallback, cron van tra ket qua', async () => {
      ai.enabled = true;
      ai.generate.mockRejectedValue(new Error('AI Space /generate timeout'));

      const results = await service.generateAiQuests();

      expect(results).toHaveLength(2);
      expect(results[0].quest.source).toBe('FALLBACK');
    });

    it('idempotent: ngay da co quest -> giu nguyen, created=false, khong goi LLM cho ngay do', async () => {
      ai.enabled = true;
      repo.getAiQuest.mockImplementation((date) =>
        Promise.resolve(date === today ? aiQuest : null),
      );
      ai.generate.mockResolvedValue({
        targetClass: 'chair',
        content: 'Chụp chiếc ghế bạn đang ngồi',
      });

      const results = await service.generateAiQuests();

      expect(results[0]).toEqual({ date: today, quest: aiQuest, created: false });
      expect(results[1].created).toBe(true);
      expect(ai.generate).toHaveBeenCalledTimes(1);
    });

    it('thua luong khac luc create -> dung quest cua luong thang (khong ghi de)', async () => {
      ai.enabled = true;
      ai.generate.mockResolvedValue({
        targetClass: 'chair',
        content: 'Chụp chiếc ghế bạn đang ngồi',
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

    it('phan trang trong bo nho + loc outcome/date/uid', async () => {
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

    it('thong ke hom nay: tong / khop / khong khop / bo qua', async () => {
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
