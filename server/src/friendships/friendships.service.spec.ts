import { BadRequestException, NotFoundException } from '@nestjs/common';
import { INVITE_LINK_BASE_URL, MAX_FRIENDS } from '../common/constants';
import { FramesService } from '../frames/frames.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { FriendshipsRepository } from './friendships.repository';
import { FriendshipsService } from './friendships.service';

describe('FriendshipsService', () => {
  let service: FriendshipsService;
  let repo: jest.Mocked<FriendshipsRepository>;
  let users: jest.Mocked<UsersService>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let framesService: jest.Mocked<FramesService>;

  /** Nguoi moi hop le: ma con han (het han sau 10 ngay nua). */
  const validInviter = () => ({
    uid: 'friend',
    inviteCodeExpiresAt: new Date(Date.now() + 10 * 24 * 60 * 60 * 1000).toISOString(),
  });

  beforeEach(() => {
    repo = {
      findPair: jest.fn(),
      listAccepted: jest.fn(),
      createPendingRequest: jest.fn(),
      acceptRequest: jest.fn(),
      declineRequest: jest.fn(),
      listPendingRequests: jest.fn(),
      delete: jest.fn(),
    } as unknown as jest.Mocked<FriendshipsRepository>;

    users = {
      findByInviteCode: jest.fn(),
      getOrCreateInviteCode: jest.fn(),
      getPublicProfile: jest.fn(),
      pushToUids: jest.fn().mockResolvedValue(0),
    } as unknown as jest.Mocked<UsersService>;

    usersRepo = {
      findByUid: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<UsersRepository>;

    framesService = {
      unlockByThreshold: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FramesService>;

    // hook FRIEND_COUNT goi listAccepted sau khi ket ban — mac dinh khong co ban
    repo.listAccepted.mockResolvedValue([]);

    service = new FriendshipsService(repo, users, usersRepo, framesService);
  });

  describe('getInviteLink', () => {
    it('returns the hosting-domain link + 30-day expiry from usersService', async () => {
      users.getOrCreateInviteCode.mockResolvedValue({
        inviteCode: 'abc123',
        expiresAt: '2026-08-18T00:00:00.000Z',
      });

      const result = await service.getInviteLink('me');

      expect(result.link).toBe(`${INVITE_LINK_BASE_URL}abc123`);
      expect(result.expiresAt).toBe('2026-08-18T00:00:00.000Z');
    });
  });

  describe('getInviteInfo', () => {
    it('returns the inviter\'s public profile + link expiry', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      users.getPublicProfile.mockResolvedValue({
        uid: 'friend',
        fullName: 'My Friend',
        avatar: 'http://a.png',
      } as never);

      const result = await service.getInviteInfo('code');

      expect(result.fullName).toBe('My Friend');
      expect(result.expiresAt).toBeDefined();
    });

    it('error when the code does not exist', async () => {
      users.findByInviteCode.mockResolvedValue(null);
      await expect(service.getInviteInfo('bad')).rejects.toThrow(NotFoundException);
    });

    it('error when the code has expired', async () => {
      users.findByInviteCode.mockResolvedValue({
        uid: 'friend',
        inviteCodeExpiresAt: new Date(Date.now() - 1000).toISOString(),
      } as never);
      await expect(service.getInviteInfo('old')).rejects.toThrow(/expired/);
    });
  });

  describe('connect (sends a request — link owner must accept)', () => {
    it('error when the invite code does not exist', async () => {
      users.findByInviteCode.mockResolvedValue(null);
      await expect(service.connect('me', 'bad')).rejects.toThrow(NotFoundException);
    });

    it('error when the invite code expired (old link invalid after 30 days)', async () => {
      users.findByInviteCode.mockResolvedValue({
        uid: 'friend',
        inviteCodeExpiresAt: new Date(Date.now() - 1000).toISOString(),
      } as never);
      await expect(service.connect('me', 'old')).rejects.toThrow(/expired/);
    });

    it('cannot invite yourself', async () => {
      users.findByInviteCode.mockResolvedValue({ ...validInviter(), uid: 'me' } as never);
      await expect(service.connect('me', 'code')).rejects.toThrow(BadRequestException);
    });

    it('creates a PENDING request when valid (not friends yet)', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({
        outcome: 'REQUESTED',
        friendship: { pairId: 'friend_me', status: 'PENDING', requesterUid: 'me' } as never,
      });

      const result = await service.connect('me', 'code');

      expect(repo.createPendingRequest).toHaveBeenCalledWith('me', 'friend', MAX_FRIENDS);
      expect(result.status).toBe('PENDING');
    });

    it('both invite each other (reverse PENDING) -> ACCEPTED immediately', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({
        outcome: 'MUTUAL_ACCEPTED',
        friendship: { pairId: 'friend_me', status: 'ACCEPTED' } as never,
      });

      const result = await service.connect('me', 'code');

      expect(result.status).toBe('ACCEPTED');
    });

    it('error when already friends', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'ALREADY_FRIENDS' });
      await expect(service.connect('me', 'code')).rejects.toThrow(/already friends/);
    });

    it('error when a request was already sent', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'ALREADY_REQUESTED' });
      await expect(service.connect('me', 'code')).rejects.toThrow(/already sent a request/i);
    });

    it('error when you exceed the 20-friend limit', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'LIMIT_REQUESTER' });
      await expect(service.connect('me', 'code')).rejects.toThrow(
        new RegExp(`${MAX_FRIENDS}-friend limit`),
      );
    });

    it('error when the link owner exceeds the 20-friend limit', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'LIMIT_INVITER' });
      await expect(service.connect('me', 'code')).rejects.toThrow(/The inviter/);
    });
  });

  describe('listRequests', () => {
    it('returns requests with sender profiles', async () => {
      repo.listPendingRequests.mockResolvedValue([
        {
          pairId: 'a_me',
          requesterUid: 'a',
          createdAt: '2026-07-19T00:00:00.000Z',
          userIds: ['a', 'me'],
          status: 'PENDING',
        } as never,
      ]);
      users.getPublicProfile.mockResolvedValue({
        uid: 'a',
        fullName: 'Sender',
        avatar: 'http://a.png',
      } as never);

      const result = await service.listRequests('me');

      expect(result).toHaveLength(1);
      expect(result[0]).toEqual({
        uid: 'a',
        fullName: 'Sender',
        avatar: 'http://a.png',
        requestedAt: '2026-07-19T00:00:00.000Z',
      });
    });
  });

  describe('acceptRequest', () => {
    it('accept -> friends ACCEPTED', async () => {
      repo.acceptRequest.mockResolvedValue({
        outcome: 'ACCEPTED',
        friendship: { pairId: 'a_me', status: 'ACCEPTED' } as never,
      });

      const result = await service.acceptRequest('me', 'a');

      expect(repo.acceptRequest).toHaveBeenCalledWith('me', 'a', MAX_FRIENDS);
      expect(result.status).toBe('ACCEPTED');
    });

    it('error when the request does not exist', async () => {
      repo.acceptRequest.mockResolvedValue({ outcome: 'NOT_FOUND' });
      await expect(service.acceptRequest('me', 'a')).rejects.toThrow(NotFoundException);
    });

    it('error when you already have 20 friends on accept', async () => {
      repo.acceptRequest.mockResolvedValue({ outcome: 'LIMIT_CURRENT' });
      await expect(service.acceptRequest('me', 'a')).rejects.toThrow(
        new RegExp(`${MAX_FRIENDS}-friend limit`),
      );
    });

    it('error when the sender already has 20 friends on accept', async () => {
      repo.acceptRequest.mockResolvedValue({ outcome: 'LIMIT_REQUESTER' });
      await expect(service.acceptRequest('me', 'a')).rejects.toThrow(/The sender/);
    });
  });

  describe('declineRequest', () => {
    it('decline -> deletes the request', async () => {
      repo.declineRequest.mockResolvedValue(true);
      await service.declineRequest('me', 'a');
      expect(repo.declineRequest).toHaveBeenCalledWith('me', 'a');
    });

    it('error when there is no matching request', async () => {
      repo.declineRequest.mockResolvedValue(false);
      await expect(service.declineRequest('me', 'a')).rejects.toThrow(NotFoundException);
    });
  });
});
