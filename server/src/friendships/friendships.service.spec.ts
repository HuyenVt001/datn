import { BadRequestException, NotFoundException } from '@nestjs/common';
import { INVITE_LINK_BASE_URL, MAX_FRIENDS } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { FriendshipsRepository } from './friendships.repository';
import { FriendshipsService } from './friendships.service';

describe('FriendshipsService', () => {
  let service: FriendshipsService;
  let repo: jest.Mocked<FriendshipsRepository>;
  let users: jest.Mocked<UsersService>;
  let usersRepo: jest.Mocked<UsersRepository>;

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
      updateStreak: jest.fn(),
    } as unknown as jest.Mocked<FriendshipsRepository>;

    users = {
      findByInviteCode: jest.fn(),
      getOrCreateInviteCode: jest.fn(),
      getPublicProfile: jest.fn(),
    } as unknown as jest.Mocked<UsersService>;

    usersRepo = {
      findByUid: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<UsersRepository>;

    const firebase = {
      messaging: jest.fn().mockReturnValue({
        sendEachForMulticast: jest.fn().mockResolvedValue({}),
      }),
    } as unknown as FirebaseService;

    service = new FriendshipsService(repo, users, usersRepo, firebase);
  });

  describe('getInviteLink', () => {
    it('tra ve link theo domain hosting + han 30 ngay tu usersService', async () => {
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
    it('tra ve ho so cong khai nguoi moi + han link', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      users.getPublicProfile.mockResolvedValue({
        uid: 'friend',
        fullName: 'Ban Toi',
        avatar: 'http://a.png',
      } as never);

      const result = await service.getInviteInfo('code');

      expect(result.fullName).toBe('Ban Toi');
      expect(result.expiresAt).toBeDefined();
    });

    it('bao loi khi ma khong ton tai', async () => {
      users.findByInviteCode.mockResolvedValue(null);
      await expect(service.getInviteInfo('bad')).rejects.toThrow(NotFoundException);
    });

    it('bao loi khi ma da het han', async () => {
      users.findByInviteCode.mockResolvedValue({
        uid: 'friend',
        inviteCodeExpiresAt: new Date(Date.now() - 1000).toISOString(),
      } as never);
      await expect(service.getInviteInfo('old')).rejects.toThrow(/het han/);
    });
  });

  describe('connect (gui loi moi — chu link phai xac nhan)', () => {
    it('bao loi khi ma moi khong ton tai', async () => {
      users.findByInviteCode.mockResolvedValue(null);
      await expect(service.connect('me', 'bad')).rejects.toThrow(NotFoundException);
    });

    it('bao loi khi ma moi het han (link cu vo hieu sau 30 ngay)', async () => {
      users.findByInviteCode.mockResolvedValue({
        uid: 'friend',
        inviteCodeExpiresAt: new Date(Date.now() - 1000).toISOString(),
      } as never);
      await expect(service.connect('me', 'old')).rejects.toThrow(/het han/);
    });

    it('khong cho tu moi chinh minh', async () => {
      users.findByInviteCode.mockResolvedValue({ ...validInviter(), uid: 'me' } as never);
      await expect(service.connect('me', 'code')).rejects.toThrow(BadRequestException);
    });

    it('tao loi moi PENDING khi hop le (chua thanh ban ngay)', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({
        outcome: 'REQUESTED',
        friendship: { pairId: 'friend_me', status: 'PENDING', requesterUid: 'me' } as never,
      });

      const result = await service.connect('me', 'code');

      expect(repo.createPendingRequest).toHaveBeenCalledWith('me', 'friend', MAX_FRIENDS);
      expect(result.status).toBe('PENDING');
    });

    it('2 ben cung moi nhau (PENDING nguoc chieu) -> ACCEPTED luon', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({
        outcome: 'MUTUAL_ACCEPTED',
        friendship: { pairId: 'friend_me', status: 'ACCEPTED' } as never,
      });

      const result = await service.connect('me', 'code');

      expect(result.status).toBe('ACCEPTED');
    });

    it('bao loi khi da la ban', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'ALREADY_FRIENDS' });
      await expect(service.connect('me', 'code')).rejects.toThrow(/da la ban/);
    });

    it('bao loi khi da gui loi moi truoc do', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'ALREADY_REQUESTED' });
      await expect(service.connect('me', 'code')).rejects.toThrow(/da gui loi moi/i);
    });

    it('bao loi khi minh vuot gioi han 20 ban', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'LIMIT_REQUESTER' });
      await expect(service.connect('me', 'code')).rejects.toThrow(
        new RegExp(`gioi han ${MAX_FRIENDS}`),
      );
    });

    it('bao loi khi chu link vuot gioi han 20 ban', async () => {
      users.findByInviteCode.mockResolvedValue(validInviter() as never);
      repo.createPendingRequest.mockResolvedValue({ outcome: 'LIMIT_INVITER' });
      await expect(service.connect('me', 'code')).rejects.toThrow(/Nguoi moi/);
    });
  });

  describe('listRequests', () => {
    it('tra ve loi moi kem ho so nguoi gui', async () => {
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
        fullName: 'Nguoi Gui',
        avatar: 'http://a.png',
      } as never);

      const result = await service.listRequests('me');

      expect(result).toHaveLength(1);
      expect(result[0]).toEqual({
        uid: 'a',
        fullName: 'Nguoi Gui',
        avatar: 'http://a.png',
        requestedAt: '2026-07-19T00:00:00.000Z',
      });
    });
  });

  describe('acceptRequest', () => {
    it('chap nhan -> thanh ban ACCEPTED', async () => {
      repo.acceptRequest.mockResolvedValue({
        outcome: 'ACCEPTED',
        friendship: { pairId: 'a_me', status: 'ACCEPTED' } as never,
      });

      const result = await service.acceptRequest('me', 'a');

      expect(repo.acceptRequest).toHaveBeenCalledWith('me', 'a', MAX_FRIENDS);
      expect(result.status).toBe('ACCEPTED');
    });

    it('bao loi khi loi moi khong ton tai', async () => {
      repo.acceptRequest.mockResolvedValue({ outcome: 'NOT_FOUND' });
      await expect(service.acceptRequest('me', 'a')).rejects.toThrow(NotFoundException);
    });

    it('bao loi khi minh da du 20 ban luc accept', async () => {
      repo.acceptRequest.mockResolvedValue({ outcome: 'LIMIT_CURRENT' });
      await expect(service.acceptRequest('me', 'a')).rejects.toThrow(
        new RegExp(`gioi han ${MAX_FRIENDS}`),
      );
    });

    it('bao loi khi nguoi gui da du 20 ban luc accept', async () => {
      repo.acceptRequest.mockResolvedValue({ outcome: 'LIMIT_REQUESTER' });
      await expect(service.acceptRequest('me', 'a')).rejects.toThrow(/Nguoi gui/);
    });
  });

  describe('declineRequest', () => {
    it('tu choi -> xoa loi moi', async () => {
      repo.declineRequest.mockResolvedValue(true);
      await service.declineRequest('me', 'a');
      expect(repo.declineRequest).toHaveBeenCalledWith('me', 'a');
    });

    it('bao loi khi khong co loi moi tuong ung', async () => {
      repo.declineRequest.mockResolvedValue(false);
      await expect(service.declineRequest('me', 'a')).rejects.toThrow(NotFoundException);
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
