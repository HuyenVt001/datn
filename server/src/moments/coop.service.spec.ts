import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { UploadService } from '../upload/upload.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CoopRepository } from './coop.repository';
import { CoopService } from './coop.service';

describe('CoopService', () => {
  let service: CoopService;
  let repo: jest.Mocked<CoopRepository>;
  let friendshipsRepo: jest.Mocked<FriendshipsRepository>;
  let friendshipsService: jest.Mocked<FriendshipsService>;
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
      update: jest.fn().mockResolvedValue(undefined),
      transition: jest.fn().mockResolvedValue(true),
    } as unknown as jest.Mocked<CoopRepository>;
    friendshipsRepo = {
      findPair: jest.fn().mockResolvedValue({ status: 'ACCEPTED' }),
    } as unknown as jest.Mocked<FriendshipsRepository>;
    friendshipsService = {
      registerInteraction: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<FriendshipsService>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue({ uid: 'x', fcmTokens: [], fullName: 'X' }),
    } as unknown as jest.Mocked<UsersRepository>;
    usersService = {
      pushToUids: jest.fn().mockResolvedValue(0),
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
      friendshipsService,
      usersRepo,
      usersService,
      framesService,
      uploadService,
    );
  });

  describe('createInvite', () => {
    const dto = { friendUid: 'bob' };

    it('chan moi chinh minh', async () => {
      await expect(service.createInvite('bob', dto)).rejects.toThrow(BadRequestException);
    });

    it('chan moi nguoi KHONG phai ban be', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null as never);
      await expect(service.createInvite('alice', dto)).rejects.toThrow(ForbiddenException);
    });

    it('tao loi moi PENDING (khong kem anh)', async () => {
      repo.create.mockResolvedValue(pendingInvite as never);

      const result = await service.createInvite('alice', dto);

      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({ inviterId: 'alice', inviteeId: 'bob', status: 'PENDING' }),
      );
      expect(repo.create).toHaveBeenCalledWith(
        expect.not.objectContaining({ inviterMediaUrl: expect.anything() }),
      );
      expect(result.inviteId).toBe('i1');
    });
  });

  describe('accept', () => {
    it('chan nguoi khong phai invitee', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.accept('stranger', 'i1')).rejects.toThrow(ForbiddenException);
    });

    it('chan loi moi da xu ly roi', async () => {
      repo.findById.mockResolvedValue({ ...pendingInvite, status: 'DECLINED' } as never);
      await expect(service.accept('bob', 'i1')).rejects.toThrow(BadRequestException);
    });

    it('chan loi moi da HET HAN (qua 5 phut)', async () => {
      repo.findById.mockResolvedValue(expiredInvite as never);
      await expect(service.accept('bob', 'i2')).rejects.toThrow(BadRequestException);
    });

    it('PENDING -> ACCEPTED qua transaction + bao FCM cho nguoi moi', async () => {
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

    it('chan accept khi decline vua khoa loi moi (transition false)', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      repo.transition.mockResolvedValue(false as never);
      await expect(service.accept('bob', 'i1')).rejects.toThrow(BadRequestException);
    });
  });

  describe('getInvite', () => {
    it('chan nguoi ngoai cuoc', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.getInvite('stranger', 'i1')).rejects.toThrow(ForbiddenException);
    });

    it('nguoi moi poll duoc trang thai loi moi cua minh', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);
      const result = await service.getInvite('alice', 'i1');
      expect(result.status).toBe('ACCEPTED');
    });

    it('PENDING qua 5 phut -> tra ve EXPIRED + danh dau vao DB', async () => {
      repo.findById.mockResolvedValue(expiredInvite as never);

      const result = await service.getInvite('alice', 'i2');

      expect(result.status).toBe('EXPIRED');
      expect(repo.update).toHaveBeenCalledWith('i2', { status: 'EXPIRED' });
    });
  });

  describe('submitMedia', () => {
    const dto = { mediaUrl: 'https://cdn/half.jpg' };

    it('chan khi loi moi CHUA duoc chap nhan', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.submitMedia('alice', 'i1', dto)).rejects.toThrow(BadRequestException);
    });

    it('nguoi moi nop nua TRAI, thieu nua kia -> giu ACCEPTED cho doi', async () => {
      repo.findById
        .mockResolvedValueOnce(acceptedInvite as never)
        .mockResolvedValueOnce({ ...acceptedInvite, inviterMediaUrl: dto.mediaUrl } as never);

      const result = await service.submitMedia('alice', 'i1', dto);

      expect(repo.update).toHaveBeenCalledWith('i1', { inviterMediaUrl: dto.mediaUrl });
      expect(result.status).toBe('ACCEPTED');
      expect(repo.transition).not.toHaveBeenCalled();
    });

    it('du 2 nua -> khoa ACCEPTED->COMPLETED, ghep + upload -> mergedMediaUrl, hook khung/streak', async () => {
      const bothHalves = {
        ...acceptedInvite,
        inviterMediaUrl: 'https://cdn/left.jpg',
        inviteeMediaUrl: 'https://cdn/right.jpg',
      };
      repo.findById
        .mockResolvedValueOnce({
          ...acceptedInvite,
          inviterMediaUrl: 'https://cdn/left.jpg',
        } as never)
        .mockResolvedValueOnce(bothHalves as never);
      const mergeSpy = mockMerge();

      const result = await service.submitMedia('bob', 'i1', dto);

      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'COMPLETED');
      // Trai = nguoi moi, phai = nguoi nhan
      expect(mergeSpy).toHaveBeenCalledWith('https://cdn/left.jpg', 'https://cdn/right.jpg');
      expect(repo.update).toHaveBeenCalledWith('i1', { mergedMediaUrl: 'https://cdn/merged.jpg' });
      expect(result.status).toBe('COMPLETED');
      expect(result.mergedMediaUrl).toBe('https://cdn/merged.jpg');
      // Friend streak + khung COOP_FIRST cho CA 2
      expect(friendshipsService.registerInteraction).toHaveBeenCalledWith('alice', 'bob');
      expect(framesService.unlockCoopFrames).toHaveBeenCalledWith('alice');
      expect(framesService.unlockCoopFrames).toHaveBeenCalledWith('bob');
    });

    it('2 ben nop cung luc: ben thua transaction KHONG ghep lai', async () => {
      const bothHalves = {
        ...acceptedInvite,
        inviterMediaUrl: 'https://cdn/left.jpg',
        inviteeMediaUrl: 'https://cdn/right.jpg',
      };
      repo.findById
        .mockResolvedValueOnce(bothHalves as never)
        .mockResolvedValueOnce(bothHalves as never);
      repo.transition.mockResolvedValue(false as never);
      const mergeSpy = mockMerge();

      const result = await service.submitMedia('bob', 'i1', dto);

      expect(mergeSpy).not.toHaveBeenCalled();
      expect(result.status).toBe('COMPLETED');
    });

    it('ghep FAIL -> tra loi moi ve ACCEPTED de nop lai', async () => {
      const bothHalves = {
        ...acceptedInvite,
        inviterMediaUrl: 'https://cdn/left.jpg',
        inviteeMediaUrl: 'https://cdn/right.jpg',
      };
      repo.findById
        .mockResolvedValueOnce(bothHalves as never)
        .mockResolvedValueOnce(bothHalves as never);
      jest
        .spyOn(
          service as unknown as { mergeSideBySide(l: string, r: string): Promise<Buffer> },
          'mergeSideBySide',
        )
        .mockRejectedValue(new BadRequestException('Khong tai duoc anh de ghep.'));

      await expect(service.submitMedia('bob', 'i1', dto)).rejects.toThrow(BadRequestException);
      expect(repo.update).toHaveBeenCalledWith('i1', { status: 'ACCEPTED' });
    });
  });

  describe('listPending', () => {
    it('bo loi moi het han khoi danh sach cho', async () => {
      repo.listPendingForInvitee.mockResolvedValue([pendingInvite, expiredInvite] as never);

      const result = await service.listPending('bob');

      expect(result).toHaveLength(1);
      expect(result[0].inviteId).toBe('i1');
    });
  });

  describe('decline', () => {
    it('nguoi nhan tu choi -> DECLINED qua transaction', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);

      await service.decline('bob', 'i1');

      expect(repo.transition).toHaveBeenCalledWith('i1', 'PENDING', 'DECLINED');
    });

    it('NGUOI MOI cung huy duoc loi moi dang cho cua minh', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await service.decline('alice', 'i1');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'PENDING', 'DECLINED');
    });

    it('chan nguoi ngoai cuoc + loi moi da xu ly', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.decline('stranger', 'i1')).rejects.toThrow(ForbiddenException);

      repo.findById.mockResolvedValue(acceptedInvite as never);
      await expect(service.decline('bob', 'i1')).rejects.toThrow(BadRequestException);
    });
  });
});
