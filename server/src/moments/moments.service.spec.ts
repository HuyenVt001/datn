import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FirebaseService } from '../firebase/firebase.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
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
  let friendshipsService: jest.Mocked<FriendshipsService>;
  let friendshipsRepo: jest.Mocked<FriendshipsRepository>;
  let questsService: jest.Mocked<QuestsService>;
  let firebase: jest.Mocked<FirebaseService>;

  const me: AuthUser = { uid: 'me' };

  beforeEach(() => {
    repo = {
      create: jest.fn(),
      findById: jest.fn(),
      listByUserIds: jest.fn(),
      listByCoopUserIds: jest.fn().mockResolvedValue([]),
      markSeen: jest.fn(),
      addReaction: jest.fn(),
      listReactions: jest.fn(),
    } as unknown as jest.Mocked<MomentsRepository>;
    usersService = {
      registerActivityForStreak: jest.fn().mockResolvedValue(1),
    } as unknown as jest.Mocked<UsersService>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<UsersRepository>;
    friendshipsService = {
      registerInteraction: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<FriendshipsService>;
    friendshipsRepo = {
      listAccepted: jest.fn().mockResolvedValue([]),
      findPair: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<FriendshipsRepository>;
    questsService = {
      registerMomentPosted: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<QuestsService>;
    firebase = {
      messaging: jest.fn(),
    } as unknown as jest.Mocked<FirebaseService>;

    service = new MomentsService(
      repo,
      usersService,
      usersRepo,
      friendshipsService,
      friendshipsRepo,
      questsService,
      firebase,
    );
  });

  describe('create', () => {
    const dto: CreateMomentDto = {
      contentType: 'PHOTO',
      mediaUrl: 'https://res.cloudinary.com/x/img.jpg',
    };

    it('tao moment + tang personal streak', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);

      const result = await service.create(me, dto);

      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ userId: 'me', contentType: 'PHOTO' }),
      );
      expect(usersService.registerActivityForStreak).toHaveBeenCalledWith('me');
      // Dang bai -> hoan thanh quest POST_MOMENT voi streak vua tinh
      expect(questsService.registerMomentPosted).toHaveBeenCalledWith('me', 1);
      expect(result.momentId).toBe('m1');
    });

    it('van dang bai thanh cong khi quest service loi', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1' } as never);
      questsService.registerMomentPosted.mockRejectedValue(new Error('quest down'));

      await expect(service.create(me, dto)).resolves.toBeDefined();
    });

    it('van dang bai thanh cong khi FCM loi', async () => {
      repo.create.mockResolvedValue({ momentId: 'm1' } as never);
      friendshipsRepo.listAccepted.mockRejectedValue(new Error('fcm down'));

      await expect(service.create(me, dto)).resolves.toBeDefined();
    });
  });

  describe('react', () => {
    it('bao loi khi moment khong ton tai', async () => {
      repo.findById.mockResolvedValue(null);
      await expect(service.react('bad', 'me', '❤️')).rejects.toThrow(NotFoundException);
    });

    it('tha reaction + cap nhat friend streak voi chu bai', async () => {
      repo.findById.mockResolvedValue({ momentId: 'm1', userId: 'owner' } as never);
      repo.addReaction.mockResolvedValue({ reactionId: 'r1', emojiType: '❤️' } as never);

      await service.react('m1', 'me', '❤️');

      expect(repo.addReaction).toHaveBeenCalledWith('m1', 'me', '❤️');
      expect(friendshipsService.registerInteraction).toHaveBeenCalledWith('me', 'owner');
    });

    it('khong tinh streak khi tu tha emoji bai cua minh', async () => {
      repo.findById.mockResolvedValue({ momentId: 'm1', userId: 'me' } as never);
      repo.addReaction.mockResolvedValue({ reactionId: 'r1' } as never);

      await service.react('m1', 'me', '🔥');

      expect(friendshipsService.registerInteraction).not.toHaveBeenCalled();
    });
  });

  describe('getFeed', () => {
    it('gop moment cua minh + ban be va phan trang', async () => {
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
    it('chi lay moment cua chinh minh', async () => {
      repo.listByUserIds.mockResolvedValue([{ momentId: 'a', userId: 'me' }] as never);

      const result = await service.listMine('me', { page: 1, limit: 20 });

      expect(repo.listByUserIds).toHaveBeenCalledWith(['me']);
      expect(result.total).toBe(1);
    });
  });

  describe('listOfUser', () => {
    it('chan xem moment cua nguoi KHONG phai ban be', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null as never);

      await expect(service.listOfUser('me', 'stranger', { page: 1, limit: 20 })).rejects.toThrow(
        ForbiddenException,
      );
    });

    it('ban be xem duoc moment cua nhau', async () => {
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);
      repo.listByUserIds.mockResolvedValue([{ momentId: 'a', userId: 'friend' }] as never);

      const result = await service.listOfUser('me', 'friend', { page: 1, limit: 20 });

      expect(repo.listByUserIds).toHaveBeenCalledWith(['friend']);
      expect(result.items).toHaveLength(1);
    });

    it('tu xem moment cua minh khong can check ban be', async () => {
      repo.listByUserIds.mockResolvedValue([] as never);

      await service.listOfUser('me', 'me', { page: 1, limit: 20 });

      expect(friendshipsRepo.findPair).not.toHaveBeenCalled();
    });
  });
});
