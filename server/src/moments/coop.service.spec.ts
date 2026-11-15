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
      // Kem dem nguoc cho app (khong luu DB)
      expect(result.remainingSeconds).toBeGreaterThan(290);
      expect(result.expiresAt).toBeDefined();
    });

    it('push loi moi kem TEN nguoi moi', async () => {
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

    it('da co loi moi CON HAN cho cung nguoi -> tra ve loi moi cu, khong tao/push lai', async () => {
      repo.listPendingBetween.mockResolvedValue([pendingInvite] as never);

      const result = await service.createInvite('alice', dto);

      expect(result.inviteId).toBe('i1');
      expect(repo.create).not.toHaveBeenCalled();
      expect(usersService.pushToUids).not.toHaveBeenCalled();
    });

    it('loi moi cu da QUA HAN -> danh dau EXPIRED roi tao loi moi moi', async () => {
      repo.listPendingBetween.mockResolvedValue([expiredInvite] as never);
      repo.create.mockResolvedValue(pendingInvite as never);

      await service.createInvite('alice', dto);

      expect(repo.transition).toHaveBeenCalledWith('i2', 'PENDING', 'EXPIRED');
      expect(repo.create).toHaveBeenCalled();
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

    it('PENDING qua 5 phut -> tra ve EXPIRED + danh dau qua TRANSITION transactional', async () => {
      repo.findById.mockResolvedValue(expiredInvite as never);

      const result = await service.getInvite('alice', 'i2');

      expect(result.status).toBe('EXPIRED');
      expect(repo.transition).toHaveBeenCalledWith('i2', 'PENDING', 'EXPIRED');
    });

    it('phien ACCEPTED qua 10 phut -> EXPIRED', async () => {
      repo.findById.mockResolvedValue(staleSession as never);

      const result = await service.getInvite('alice', 'i1');

      expect(result.status).toBe('EXPIRED');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'EXPIRED');
    });

    it('phien ACCEPTED con han -> kem remainingSeconds cua phien', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);

      const result = await service.getInvite('alice', 'i1');

      expect(result.remainingSeconds).toBeGreaterThan(590);
    });

    it('thua transition EXPIRED (accept vua thang) -> tra ve trang thai THAT tu DB', async () => {
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

    it('chan URL KHONG phai Cloudinary (SSRF)', async () => {
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

    it('chan nop anh khi phien da qua han 10 phut', async () => {
      repo.findById.mockResolvedValue(staleSession as never);

      await expect(service.submitMedia('alice', 'i1', dto)).rejects.toThrow(BadRequestException);
      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'EXPIRED');
      expect(repo.update).not.toHaveBeenCalled();
    });

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

    it('2 ben nop cung luc: ben thua transaction KHONG ghep lai, tra ve trang thai THAT', async () => {
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

    it('ghep FAIL -> tra loi moi ve ACCEPTED de nop lai', async () => {
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
        .mockRejectedValue(new BadRequestException('Khong tai duoc anh de ghep.'));

      await expect(service.submitMedia('bob', 'i1', dto)).rejects.toThrow(BadRequestException);
      // Revert qua TRANSITION co khoa, khong update tran
      expect(repo.transition).toHaveBeenCalledWith('i1', 'COMPLETED', 'ACCEPTED');
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

    it('NGUOI MOI cung huy duoc loi moi dang cho cua minh + bao nguoi nhan (data-only)', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await service.decline('alice', 'i1');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'PENDING', 'DECLINED');
      expect(usersService.pushDataToUids).toHaveBeenCalledWith(['bob'], {
        type: 'COOP_CANCELLED',
        inviteId: 'i1',
      });
    });

    it('nguoi nhan tu choi -> KHONG gui push huy', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await service.decline('bob', 'i1');
      expect(usersService.pushDataToUids).not.toHaveBeenCalled();
    });

    it('HUY duoc phien dang ACCEPTED (roi man chup) — ca 2 phia', async () => {
      repo.findById.mockResolvedValue(acceptedInvite as never);

      await service.decline('alice', 'i1');
      expect(repo.transition).toHaveBeenCalledWith('i1', 'ACCEPTED', 'DECLINED');

      await service.decline('bob', 'i1');
      expect(repo.transition).toHaveBeenCalledTimes(2);
    });

    it('chan nguoi ngoai cuoc + loi moi da COMPLETED', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.decline('stranger', 'i1')).rejects.toThrow(ForbiddenException);

      repo.findById.mockResolvedValue({ ...pendingInvite, status: 'COMPLETED' } as never);
      await expect(service.decline('bob', 'i1')).rejects.toThrow(BadRequestException);
    });
  });
});
