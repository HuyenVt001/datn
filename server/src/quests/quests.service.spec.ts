import { AstriteService } from '../astrite/astrite.service';
import { QUEST_DAILY_ASTRITE } from '../common/constants';
import { FramesRepository } from '../frames/frames.repository';
import { FramesService } from '../frames/frames.service';
import { QuestType, UserQuest } from './entities/quest.entity';
import { QuestsRepository } from './quests.repository';
import { QuestsService } from './quests.service';

describe('QuestsService', () => {
  let service: QuestsService;
  let repo: jest.Mocked<QuestsRepository>;
  let astrite: jest.Mocked<AstriteService>;
  let framesService: jest.Mocked<FramesService>;
  let framesRepo: jest.Mocked<FramesRepository>;

  const today = new Date().toISOString().slice(0, 10);

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

    service = new QuestsService(repo, astrite, framesService, framesRepo);
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
});
