import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { QuestsService } from '../quests/quests.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CreateMomentDto } from './dto/create-moment.dto';
import { MomentsRepository } from './moments.repository';
import { MomentsService } from './moments.service';

describe('MomentsService', () => {
  let service: MomentsService;
  let repo: jest.Mocked<MomentsRepository>;
  let usersService: jest.Mocked<UsersService>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let friendshipsRepo: jest.Mocked<FriendshipsRepository>;
  let questsService: jest.Mocked<QuestsService>;
  let framesService: jest.Mocked<FramesService>;

  const me: AuthUser = { uid: 'me' };

  beforeEach(() => {
    repo = {
      create: jest.fn(),
      findById: jest.fn(),
      countByUserId: jest.fn().mockResolvedValue(1),
      findByClientRequestId: jest.fn().mockResolvedValue(null),
      listByUserIds: jest.fn(),
      listByCoopUserIds: jest.fn().mockResolvedValue([]),
      markSeen: jest.fn(),
      addReaction: jest.fn(),
      listReactions: jest.fn(),
    } as unknown as jest.Mocked<MomentsRepository>;
    usersService = {
      registerActivityForStreakDetailed: jest
        .fn()
        .mockResolvedValue({ personalStreak: 1, increased: true }),
      pushToUids: jest.fn().mockResolvedValue(0),
    } as unknown as jest.Mocked<UsersService>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<UsersRepository>;
    friendshipsRepo = {
      listAccepted: jest.fn().mockResolvedValue([]),
      findPair: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<FriendshipsRepository>;
    questsService = {
      registerMomentPosted: jest.fn().mockResolvedValue(undefined),
      verifyAiQuest: jest.fn().mockResolvedValue(undefined), // mac dinh: khong co gi de xac minh
    } as unknown as jest.Mocked<QuestsService>;
    framesService = {
      unlockByThreshold: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FramesService>;

    service = new MomentsService(
      repo,
      usersService,
      usersRepo,
      friendshipsRepo,
      questsService,
      framesService,
    );
  });

  describe('create', () => {
    const dto: CreateMomentDto = {
      contentType: 'PHOTO',
      mediaUrl: 'https://res.cloudinary.com/x/img.jpg',
    };

    it('creates a moment + increases personal streak', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);

      const result = await service.create(me, dto);

      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ userId: 'me', contentType: 'PHOTO' }),
      );
      expect(usersService.registerActivityForStreakDetailed).toHaveBeenCalledWith('me');
      // Dang bai -> hoan thanh quest POST_MOMENT voi streak vua tinh
      expect(questsService.registerMomentPosted).toHaveBeenCalledWith('me', 1);
      expect(result.momentId).toBe('m1');
    });

    // ==== Field streak cho man an mung cua app (2026-11-15) ====

    it('first post of the day -> streak.increased true, no milestone hit -> milestone null', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);

      const result = await service.create(me, dto);

      expect(result.streak).toEqual({
        personalStreak: 1,
        increased: true,
        milestone: null,
        unlockedFrameId: null,
      });
    });

    it('hits milestone 7 + unlocks frame -> milestone 7 and unlockedFrameId', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      usersService.registerActivityForStreakDetailed.mockResolvedValue({
        personalStreak: 7,
        increased: true,
      });
      questsService.registerMomentPosted.mockResolvedValue('frame_streak_7');

      const result = await service.create(me, dto);

      expect(result.streak).toEqual({
        personalStreak: 7,
        increased: true,
        milestone: 7,
        unlockedFrameId: 'frame_streak_7',
      });
    });

    it('second post of the day -> increased false, no milestone even with streak = 7', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      usersService.registerActivityForStreakDetailed.mockResolvedValue({
        personalStreak: 7,
        increased: false,
      });
      questsService.registerMomentPosted.mockResolvedValue('frame_streak_7');

      const result = await service.create(me, dto);

      expect(result.streak).toEqual({
        personalStreak: 7,
        increased: false,
        milestone: null,
        unlockedFrameId: null,
      });
    });

    it('streak update error -> response has NO streak field, post still published', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      usersService.registerActivityForStreakDetailed.mockRejectedValue(new Error('db down'));

      const result = await service.create(me, dto);

      expect(result.momentId).toBe('m1');
      expect('streak' in result).toBe(false);
    });

    it('post still succeeds when the quest service fails', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1' } as never);
      questsService.registerMomentPosted.mockRejectedValue(new Error('quest down'));

      await expect(service.create(me, dto)).resolves.toBeDefined();
    });

    // ==== Quest AI (2026-08-15) ====

    it('nothing to verify (AI off / already done) -> response has NO aiQuest field', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);

      const result = await service.create(me, dto);

      expect(questsService.verifyAiQuest).toHaveBeenCalledWith(
        'me',
        expect.objectContaining({ momentId: 'm1' }),
      );
      expect('aiQuest' in result).toBe(false);
    });

    it('AI verification matches -> response includes aiQuest MATCHED (post is still the new moment)', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      questsService.verifyAiQuest.mockResolvedValue({
        result: 'MATCHED',
        score: 0.9,
        questContent: 'Take a photo of a cup',
      });

      const result = await service.create(me, dto);

      expect(result.momentId).toBe('m1');
      expect(result.aiQuest).toEqual({
        result: 'MATCHED',
        score: 0.9,
        questContent: 'Take a photo of a cup',
      });
    });

    it('verifyAiQuest throws (unexpected) -> post still published, no aiQuest', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      questsService.verifyAiQuest.mockRejectedValue(new Error('boom'));

      const result = await service.create(me, dto);

      expect(result.momentId).toBe('m1');
      expect(result.aiQuest).toBeUndefined();
    });

    it('post still succeeds when FCM fails', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1' } as never);
      friendshipsRepo.listAccepted.mockRejectedValue(new Error('fcm down'));

      await expect(service.create(me, dto)).resolves.toBeDefined();
    });

    it('retry with the SAME clientRequestId -> returns the ALREADY published post, no copy', async () => {
      repo.findByClientRequestId.mockResolvedValue({ momentId: 'm-old', userId: 'me' } as never);

      const result = await service.create(me, { ...dto, clientRequestId: 'req-1' });

      expect(result.momentId).toBe('m-old');
      expect(repo.create).not.toHaveBeenCalled();
    });

    it('NEW clientRequestId -> creates the post + stores the id to dedupe later retries', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1' } as never);

      await service.create(me, { ...dto, clientRequestId: 'req-1' });

      expect(repo.findByClientRequestId).toHaveBeenCalledWith('me', 'req-1');
      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ clientRequestId: 'req-1' }),
      );
    });
  });

  describe('react', () => {
    it('error when the moment does not exist', async () => {
      repo.findById.mockResolvedValue(null);
      await expect(service.react('bad', 'me', '❤️')).rejects.toThrow(NotFoundException);
    });

    it('reacts (friends with the owner)', async () => {
      repo.findById.mockResolvedValue({ momentId: 'm1', userId: 'owner' } as never);
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);
      repo.addReaction.mockResolvedValue({ reactionId: 'r1', emojiType: '❤️' } as never);

      await service.react('m1', 'me', '❤️');

      expect(repo.addReaction).toHaveBeenCalledWith('m1', 'me', '❤️');
    });

    it('a STRANGER (not a friend) reacts -> 403, writes nothing', async () => {
      repo.findById.mockResolvedValue({ momentId: 'm1', userId: 'owner' } as never);
      friendshipsRepo.findPair.mockResolvedValue(null);

      await expect(service.react('m1', 'stranger', '❤️')).rejects.toThrow(ForbiddenException);
      expect(repo.addReaction).not.toHaveBeenCalled();
    });

    it('a friend of the CO-OP PARTNER (not the author\'s friend) can still interact', async () => {
      repo.findById.mockResolvedValue({
        momentId: 'm1',
        userId: 'author',
        coopUserId: 'partner',
      } as never);
      // khong phai ban cua tac gia, nhung la ban cua nguoi chup chung
      friendshipsRepo.findPair
        .mockResolvedValueOnce(null)
        .mockResolvedValueOnce({ status: 'ACCEPTED' } as never);
      repo.addReaction.mockResolvedValue({ reactionId: 'r1' } as never);

      await expect(service.react('m1', 'partner-friend', '❤️')).resolves.toBeDefined();
    });

    it('reacting to your own post is allowed', async () => {
      repo.findById.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      repo.addReaction.mockResolvedValue({ reactionId: 'r1' } as never);

      await expect(service.react('m1', 'me', '🔥')).resolves.toBeDefined();
    });
  });

  describe('getFeed', () => {
    it('merges your moments + friends\' and paginates', async () => {
      friendshipsRepo.listAccepted.mockResolvedValue([{ userIds: ['friend', 'me'] }] as never);
      repo.listByUserIds.mockResolvedValue([
        { momentId: 'a', postTime: '2026-07-12' },
        { momentId: 'b', postTime: '2026-07-11' },
      ] as never);

      const result = await service.getFeed('me', { page: 1, limit: 1 });

      expect(repo.listByUserIds).toHaveBeenCalledWith(['me', 'friend']);
      expect(result.items).toHaveLength(1);
      expect(result.total).toBe(2);
    });
  });

  describe('listMine', () => {
    it('only fetches your own moments', async () => {
      repo.listByUserIds.mockResolvedValue([{ momentId: 'a', userId: 'me' }] as never);

      const result = await service.listMine('me', { page: 1, limit: 20 });

      expect(repo.listByUserIds).toHaveBeenCalledWith(['me']);
      expect(result.total).toBe(1);
    });
  });

  describe('listOfUser', () => {
    it('blocks viewing moments of someone who is NOT a friend', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null as never);

      await expect(service.listOfUser('me', 'stranger', { page: 1, limit: 20 })).rejects.toThrow(
        ForbiddenException,
      );
    });

    it('friends can view each other\'s moments', async () => {
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);
      repo.listByUserIds.mockResolvedValue([{ momentId: 'a', userId: 'friend' }] as never);

      const result = await service.listOfUser('me', 'friend', { page: 1, limit: 20 });

      expect(repo.listByUserIds).toHaveBeenCalledWith(['friend']);
      expect(result.items).toHaveLength(1);
    });

    it('viewing your own moments needs no friend check', async () => {
      repo.listByUserIds.mockResolvedValue([] as never);

      await service.listOfUser('me', 'me', { page: 1, limit: 20 });

      expect(friendshipsRepo.findPair).not.toHaveBeenCalled();
    });
  });
});
