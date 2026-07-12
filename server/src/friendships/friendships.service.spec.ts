import { BadRequestException, NotFoundException } from '@nestjs/common';
import { MAX_FRIENDS } from '../common/constants';
import { UsersService } from '../users/users.service';
import { FriendshipsRepository } from './friendships.repository';
import { FriendshipsService } from './friendships.service';

describe('FriendshipsService', () => {
  let service: FriendshipsService;
  let repo: jest.Mocked<FriendshipsRepository>;
  let users: jest.Mocked<UsersService>;

  beforeEach(() => {
    repo = {
      countAccepted: jest.fn(),
      findPair: jest.fn(),
      listAccepted: jest.fn(),
      create: jest.fn(),
      delete: jest.fn(),
      updateStreak: jest.fn(),
    } as unknown as jest.Mocked<FriendshipsRepository>;

    users = {
      findByInviteCode: jest.fn(),
      getOrCreateInviteCode: jest.fn(),
      getPublicProfile: jest.fn(),
    } as unknown as jest.Mocked<UsersService>;

    service = new FriendshipsService(repo, users);
  });

  describe('connect', () => {
    it('bao loi khi ma moi khong ton tai', async () => {
      users.findByInviteCode.mockResolvedValue(null);
      await expect(service.connect('me', 'bad')).rejects.toThrow(NotFoundException);
    });

    it('khong cho tu ket ban', async () => {
      users.findByInviteCode.mockResolvedValue({ uid: 'me' } as never);
      await expect(service.connect('me', 'code')).rejects.toThrow(BadRequestException);
    });

    it('bao loi khi da la ban', async () => {
      users.findByInviteCode.mockResolvedValue({ uid: 'friend' } as never);
      repo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);
      await expect(service.connect('me', 'code')).rejects.toThrow(/da la ban/);
    });

    it('bao loi khi vuot gioi han 20 ban', async () => {
      users.findByInviteCode.mockResolvedValue({ uid: 'friend' } as never);
      repo.findPair.mockResolvedValue(null);
      repo.countAccepted.mockResolvedValue(MAX_FRIENDS);
      await expect(service.connect('me', 'code')).rejects.toThrow(BadRequestException);
    });

    it('tao friendship khi hop le', async () => {
      users.findByInviteCode.mockResolvedValue({ uid: 'friend' } as never);
      repo.findPair.mockResolvedValue(null);
      repo.countAccepted.mockResolvedValue(0);
      repo.create.mockResolvedValue({ pairId: 'friend_me', status: 'ACCEPTED' } as never);

      const result = await service.connect('me', 'code');

      expect(repo.create).toHaveBeenCalledWith('me', 'friend');
      expect(result.status).toBe('ACCEPTED');
    });
  });

  describe('registerInteraction', () => {
    it('reset streak ve 1 khi qua 24h', async () => {
      const old = new Date(Date.now() - 30 * 60 * 60 * 1000).toISOString();
      repo.findPair.mockResolvedValue({
        pairId: 'p',
        status: 'ACCEPTED',
        friendStreak: 5,
        lastInteractionAt: old,
      } as never);

      await service.registerInteraction('a', 'b');

      expect(repo.updateStreak).toHaveBeenCalledWith('p', 1, expect.any(String));
    });

    it('bo qua khi chua la ban', async () => {
      repo.findPair.mockResolvedValue(null);
      await service.registerInteraction('a', 'b');
      expect(repo.updateStreak).not.toHaveBeenCalled();
    });
  });
});
