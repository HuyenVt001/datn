import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { UploadService } from '../upload/upload.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CoopRepository } from './coop.repository';
import { CoopService } from './coop.service';

describe('CoopService', () => {
  let service: CoopService;
  let repo: jest.Mocked<CoopRepository>;
  let friendshipsRepo: jest.Mocked<FriendshipsRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let usersService: jest.Mocked<UsersService>;
  let framesService: jest.Mocked<FramesService>;
  let uploadService: jest.Mocked<UploadService>;

  // createdAt dong (vua tao) de khong dinh logic het han 5 phut theo luc chay test
  const pendingInvite = {
    inviteId: 'i1',
    inviterId: 'alice',
    inviteeId: 'bob',
    status: 'PENDING' as const,
    createdAt: new Date().toISOString(),
  };

  /** Loi moi tao tu 6 phut truoc — da qua han 5 phut. */
  const expiredInvite = {
    ...pendingInvite,
    inviteId: 'i2',
    createdAt: new Date(Date.now() - 6 * 60 * 1000).toISOString(),
  };

  /** Loi moi da duoc chap nhan — 2 ben dang o man chup. */
  const acceptedInvite = {
    ...pendingInvite,
    status: 'ACCEPTED' as const,
    respondedAt: new Date().toISOString(),
  };

  /** Phien ACCEPTED tu 11 phut truoc — qua han phien 10 phut. */
  const staleSession = {
    ...acceptedInvite,
    respondedAt: new Date(Date.now() - 11 * 60 * 1000).toISOString(),
  };

  function mockMerge() {
    return jest
      .spyOn(
        service as unknown as { mergeSideBySide(l: string, r: string): Promise<Buffer> },
        'mergeSideBySide',
      )
      .mockResolvedValue(Buffer.from('merged'));
  }

  beforeEach(() => {
    repo = {
      create: jest.fn(),
      findById: jest.fn(),
      listPendingForInvitee: jest.fn().mockResolvedValue([]),
      listPendingBetween: jest.fn().mockResolvedValue([]),
      update: jest.fn().mockResolvedValue(undefined),
      transition: jest.fn().mockResolvedValue(true),
    } as unknown as jest.Mocked<CoopRepository>;
    friendshipsRepo = {
      findPair: jest.fn().mockResolvedValue({ status: 'ACCEPTED' }),
    } as unknown as jest.Mocked<FriendshipsRepository>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue({ uid: 'x', fcmTokens: [], fullName: 'X' }),
    } as unknown as jest.Mocked<UsersRepository>;
    usersService = {
      pushToUids: jest.fn().mockResolvedValue(0),
      pushDataToUids: jest.fn().mockResolvedValue(0),
    } as unknown as jest.Mocked<UsersService>;
    framesService = {
      unlockCoopFrames: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FramesService>;
    uploadService = {
      uploadBuffer: jest.fn().mockResolvedValue({ url: 'https://cdn/merged.jpg' }),
    } as unknown as jest.Mocked<UploadService>;

    service = new CoopService(
      repo,
      friendshipsRepo,
      usersRepo,
      usersService,
      framesService,
      uploadService,
    );
  });

  describe('createInvite', () => {
    const dto = { friendUid: 'bob' };

    it('blocks inviting yourself', async () => {
      await expect(service.createInvite('bob', dto)).rejects.toThrow(BadRequestException);
    });

    it('blocks inviting someone who is NOT a friend', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null as never);
      await expect(service.createInvite('alice', dto)).rejects.toThrow(ForbiddenException);
    });

    it('creates a PENDING invite (no photo)', async () => {
      repo.create.mockResolvedValue(pendingInvite as never);

      const result = await service.createInvite('alice', dto);

      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ inviterId: 'alice', inviteeId: 'bob', status: 'PENDING' }),
      );
      expect(repo.create).toHaveBeenCalledWith(
        expect.not.objectContaining({ inviterMediaUrl: expect.anything() }),
      );
      expect(result.inviteId).toBe('i1');
      // Kem dem nguoc cho app (khong luu DB)
      expect(result.remainingSeconds).toBeGreaterThan(290);
      expect(result.expiresAt).toBeDefined();
    });

    it('invite push includes the inviter\'s NAME', async () => {
      repo.create.mockResolvedValue(pendingInvite as never);
      usersRepo.findByUid.mockResolvedValue({ uid: 'alice', fullName: 'Alice' } as never);

      await service.createInvite('alice', dto);

      expect(usersService.pushToUids).toHaveBeenCalledWith(
        ['bob'],
        expect.stringContaining('Alice'),
        expect.any(String),
        expect.objectContaining({ type: 'COOP_INVITE' }),
      );
    });

    it('an UNEXPIRED invite to the same person exists -> returns it, no new invite/push', async () => {
      repo.listPendingBetween.mockResolvedValue([pendingInvite] as never);

      const result = await service.createInvite('alice', dto);

      expect(result.inviteId).toBe('i1');
      expect(repo.create).not.toHaveBeenCalled();
      expect(usersService.pushToUids).not.toHaveBeenCalled();
    });

    it('old invite EXPIRED -> marks EXPIRED then creates a new one', async () => {
      repo.listPendingBetween.mockResolvedValue([expiredInvite] as never);
      repo.create.mockResolvedValue(pendingInvite as never);

      await service.createInvite('alice', dto);

      expect(repo.transition).toHaveBeenCalledWith('i2', 'PENDING', 'EXPIRED');
      expect(repo.create).toHaveBeenCalled();
    });
  });

  describe('accept', () => {
    it('blocks non-invitees', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.accept('stranger', 'i1')).rejects.toThrow(ForbiddenException);
    });

    it('blocks already-handled invites', async () => {
      repo.findById.mockResolvedValue({ ...pendingInvite, status: 'DECLINED' } as never);
      await expect(service.accept('bob', 'i1')).rejects.toThrow(BadRequestException);
    });

    it('blocks EXPIRED invites (over 5 minutes)', async () => {
      repo.findById.mockResolvedValue(expiredInvite as never);
      await expect(service.accept('bob', 'i2')).rejects.toThrow(BadRequestException);
    });

    it('PENDING -> ACCEPTED via transaction + FCM to the inviter', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);

      const result = await service.accept('bob', 'i1');

      expect(repo.transition).toHaveBeenCalledWith('i1', 'PENDING', 'ACCEPTED');
      expect(result.status).toBe('ACCEPTED');
      expect(usersService.pushToUids).toHaveBeenCalledWith(
        ['alice'],
        expect.any(String),
        expect.any(String),
        expect.objectContaining({ type: 'COOP_ACCEPTED' }),
      );
    });

    it('blocks accept when a decline just locked the invite (transition false)', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      repo.transition.mockResolvedValue(false as never);
      await expect(service.accept('bob', 'i1')).rejects.toThrow(BadRequestException);
    });
  });

  describe('getInvite', () => {
    it('blocks outsiders', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.getInvite('stranger', 'i1')).rejects.toThrow(ForbiddenException);
    });

    it('inviter can poll their invite status', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);
      const result = await service.getInvite('alice', 'i1');
      expect(result.status).toBe('ACCEPTED');
    });

    it('PENDING over 5 minutes -> returns EXPIRED + marks via transactional TRANSITION', async () => {
      repo.findById.mockResolvedValue(expiredInvite as never);

      const result = await service.getInvite('alice', 'i2');

      expect(result.status).toBe('EXPIRED');
      expect(repo.transition).toHaveBeenCalledWith('i2', 'PENDING', 'EXPIRED');
    });

    it('ACCEPTED session over 10 minutes -> EXPIRED', async () => {
      repo.findById.mockResolvedValue(staleSession as never);

      const result = await service.getInvite('alice', 'i1');

      expect(result.status).toBe('EXPIRED');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'EXPIRED');
    });

    it('unexpired ACCEPTED session -> includes the session\'s remainingSeconds', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);

      const result = await service.getInvite('alice', 'i1');

      expect(result.remainingSeconds).toBeGreaterThan(590);
    });

    it('loses the EXPIRED transition (accept just won) -> returns the REAL status from DB', async () => {
      repo.findById
        .mockResolvedValueOnce(expiredInvite as never)
        .mockResolvedValueOnce({ ...expiredInvite, status: 'ACCEPTED' } as never);
      repo.transition.mockResolvedValue(false as never);

      const result = await service.getInvite('alice', 'i2');

      expect(result.status).toBe('ACCEPTED');
    });
  });

  describe('submitMedia', () => {
    const dto = { mediaUrl: 'https://res.cloudinary.com/demo/half.jpg' };

    it('blocks NON-Cloudinary URLs (SSRF)', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);
      for (const mediaUrl of [
        'http://169.254.169.254/latest/meta-data',
        'https://evil.example.com/a.jpg',
        'http://res.cloudinary.com/demo/a.jpg',
      ]) {
        await expect(service.submitMedia('alice', 'i1', { mediaUrl })).rejects.toThrow(
          BadRequestException,
        );
      }
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('blocks submitting after the 10-minute session expired', async () => {
      repo.findById.mockResolvedValue(staleSession as never);

      await expect(service.submitMedia('alice', 'i1', dto)).rejects.toThrow(BadRequestException);
      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'EXPIRED');
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('blocks when the invite is NOT accepted yet', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.submitMedia('alice', 'i1', dto)).rejects.toThrow(BadRequestException);
    });

    it('inviter submits the LEFT half, other half missing -> stays ACCEPTED and waits', async () => {
      repo.findById
        .mockResolvedValueOnce(acceptedInvite as never)
        .mockResolvedValueOnce({ ...acceptedInvite, inviterMediaUrl: dto.mediaUrl } as never);

      const result = await service.submitMedia('alice', 'i1', dto);

      expect(repo.update).toHaveBeenCalledWith('i1', { inviterMediaUrl: dto.mediaUrl });
      expect(result.status).toBe('ACCEPTED');
      expect(repo.transition).not.toHaveBeenCalled();
    });

    it('both halves -> locks ACCEPTED->COMPLETED, merge + upload -> mergedMediaUrl, frame/streak hooks', async () => {
      const bothHalves = {
        ...acceptedInvite,
        inviterMediaUrl: 'https://res.cloudinary.com/demo/left.jpg',
        inviteeMediaUrl: 'https://res.cloudinary.com/demo/right.jpg',
      };
      repo.findById
        .mockResolvedValueOnce({
          ...acceptedInvite,
          inviterMediaUrl: 'https://res.cloudinary.com/demo/left.jpg',
        } as never)
        .mockResolvedValueOnce(bothHalves as never);
      const mergeSpy = mockMerge();

      const result = await service.submitMedia('bob', 'i1', dto);

      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'COMPLETED');
      // Trai = nguoi moi, phai = nguoi nhan
      expect(mergeSpy).toHaveBeenCalledWith(
        'https://res.cloudinary.com/demo/left.jpg',
        'https://res.cloudinary.com/demo/right.jpg',
      );
      expect(repo.update).toHaveBeenCalledWith('i1', { mergedMediaUrl: 'https://cdn/merged.jpg' });
      expect(result.status).toBe('COMPLETED');
      expect(result.mergedMediaUrl).toBe('https://cdn/merged.jpg');
      // Khung COOP_FIRST cho CA 2 (friend streak da bo 2026-11-15)
      expect(framesService.unlockCoopFrames).toHaveBeenCalledWith('alice');
      expect(framesService.unlockCoopFrames).toHaveBeenCalledWith('bob');
    });

    it('both submit at once: the transaction loser does NOT merge again, returns the REAL status', async () => {
      const bothHalves = {
        ...acceptedInvite,
        inviterMediaUrl: 'https://res.cloudinary.com/demo/left.jpg',
        inviteeMediaUrl: 'https://res.cloudinary.com/demo/right.jpg',
      };
      repo.findById
        .mockResolvedValueOnce(bothHalves as never)
        .mockResolvedValueOnce(bothHalves as never)
        // Doc lai sau khi thua transition: ben kia da ghep xong
        .mockResolvedValueOnce({
          ...bothHalves,
          status: 'COMPLETED',
          mergedMediaUrl: 'https://cdn/merged.jpg',
        } as never);
      repo.transition.mockResolvedValue(false as never);
      const mergeSpy = mockMerge();

      const result = await service.submitMedia('bob', 'i1', dto);

      expect(mergeSpy).not.toHaveBeenCalled();
      expect(result.status).toBe('COMPLETED');
      expect(result.mergedMediaUrl).toBe('https://cdn/merged.jpg');
    });

    it('merge FAILS -> invite returns to ACCEPTED for resubmission', async () => {
      const bothHalves = {
        ...acceptedInvite,
        inviterMediaUrl: 'https://res.cloudinary.com/demo/left.jpg',
        inviteeMediaUrl: 'https://res.cloudinary.com/demo/right.jpg',
      };
      repo.findById
        .mockResolvedValueOnce(bothHalves as never)
        .mockResolvedValueOnce(bothHalves as never);
      jest
        .spyOn(
          service as unknown as { mergeSideBySide(l: string, r: string): Promise<Buffer> },
          'mergeSideBySide',
        )
        .mockRejectedValue(new BadRequestException('Couldn\'t download the photos to merge.'));

      await expect(service.submitMedia('bob', 'i1', dto)).rejects.toThrow(BadRequestException);
      // Revert qua TRANSITION co khoa, khong update tran
      expect(repo.transition).toHaveBeenCalledWith('i1', 'COMPLETED', 'ACCEPTED');
    });
  });

  describe('listPending', () => {
    it('drops expired invites from the pending list', async () => {
      repo.listPendingForInvitee.mockResolvedValue([pendingInvite, expiredInvite] as never);

      const result = await service.listPending('bob');

      expect(result).toHaveLength(1);
      expect(result[0].inviteId).toBe('i1');
    });
  });

  describe('decline', () => {
    it('invitee declines -> DECLINED via transaction', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);

      await service.decline('bob', 'i1');

      expect(repo.transition).toHaveBeenCalledWith('i1', 'PENDING', 'DECLINED');
    });

    it('the INVITER can also cancel their pending invite + notify the invitee (data-only)', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await service.decline('alice', 'i1');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'PENDING', 'DECLINED');
      expect(usersService.pushDataToUids).toHaveBeenCalledWith(['bob'], {
        type: 'COOP_CANCELLED',
        inviteId: 'i1',
      });
    });

    it('invitee declines -> NO cancel push', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await service.decline('bob', 'i1');
      expect(usersService.pushDataToUids).not.toHaveBeenCalled();
    });

    it('can CANCEL an ACCEPTED session (leaving the capture screen) — both sides', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);

      await service.decline('alice', 'i1');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'DECLINED');

      await service.decline('bob', 'i1');
      expect(repo.transition).toHaveBeenCalledTimes(2);
    });

    it('blocks outsiders + COMPLETED invites', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.decline('stranger', 'i1')).rejects.toThrow(ForbiddenException);

      repo.findById.mockResolvedValue({ ...pendingInvite, status: 'COMPLETED' } as never);
      await expect(service.decline('bob', 'i1')).rejects.toThrow(BadRequestException);
    });
  });
});
