import { FramesRepository } from '../frames/frames.repository';
import { FramesService } from '../frames/frames.service';
import { UsersRepository } from '../users/users.repository';
import { QuestType, UserQuest } from './entities/quest.entity';
import { QuestsRepository } from './quests.repository';
import { QuestsService } from './quests.service';

describe('QuestsService', () => {
  let service: QuestsService;
  let repo: jest.Mocked<QuestsRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
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
      setDailyRewardFrame: jest.fn().mockResolvedValue(undefined),
      countCompletionsByDate: jest.fn().mockResolvedValue(0),
    } as unknown as jest.Mocked<QuestsRepository>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue({ uid: 'me', unlockedFrames: [] }),
    } as unknown as jest.Mocked<UsersRepository>;
    framesService = {
      unlockForUser: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<FramesService>;
    framesRepo = {
      list: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FramesRepository>;

    service = new QuestsService(repo, usersRepo, framesService, framesRepo);
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

    it('xong 2/2 quest -> thuong ngau nhien 1 khung chua so huu (khong tinh khung moc)', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      framesRepo.list.mockResolvedValue([
        { frameId: 'f1', frameName: 'Thuong', unlockType: 'QUEST_RANDOM', createdAt: '' },
        // khong duoc chon — khung moc streak
        {
          frameId: 'f2',
          frameName: 'Moc 7',
          unlockType: 'STREAK_MILESTONE',
          unlockValue: 7,
          milestone: 7,
          createdAt: '',
        },
        { frameId: 'f3', frameName: 'Da co', unlockType: 'QUEST_RANDOM', createdAt: '' },
      ]);
      usersRepo.findByUid.mockResolvedValue({ uid: 'me', unlockedFrames: ['f3'] } as never);

      await service.registerMomentPosted('me', 1);

      expect(framesService.unlockForUser).toHaveBeenCalledWith('me', 'f1');
      expect(repo.setDailyRewardFrame).toHaveBeenCalledWith(today, 'me', 'f1');
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

      expect(framesService.unlockForUser).not.toHaveBeenCalled();
      expect(repo.setDailyRewardFrame).not.toHaveBeenCalled();
    });

    it('het khung de thuong -> claim van ghi dau da xet (frameId null tu claim)', async () => {
      repo.getUserQuests.mockResolvedValue(
        new Map([
          ['LOGIN', userQuest('LOGIN')],
          ['POST_MOMENT', userQuest('POST_MOMENT')],
        ]),
      );
      framesRepo.list.mockResolvedValue([]);

      await service.registerMomentPosted('me', 1);

      expect(repo.tryClaimDailyReward).toHaveBeenCalledWith(today, 'me');
      expect(framesService.unlockForUser).not.toHaveBeenCalled();
      // claim da ghi doc voi frameId null — khong can setDailyRewardFrame nua
      expect(repo.setDailyRewardFrame).not.toHaveBeenCalled();
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
