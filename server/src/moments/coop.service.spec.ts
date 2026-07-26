import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { QuestsService } from '../quests/quests.service';
import { UploadService } from '../upload/upload.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CoopRepository } from './coop.repository';
import { CoopService } from './coop.service';
import { MomentsService } from './moments.service';

describe('CoopService', () => {
  let service: CoopService;
  let repo: jest.Mocked<CoopRepository>;
  let momentsService: jest.Mocked<MomentsService>;
  let friendshipsRepo: jest.Mocked<FriendshipsRepository>;
  let friendshipsService: jest.Mocked<FriendshipsService>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let usersService: jest.Mocked<UsersService>;
  let questsService: jest.Mocked<QuestsService>;
  let framesService: jest.Mocked<FramesService>;
  let uploadService: jest.Mocked<UploadService>;

  // createdAt dong (vua tao) de khong dinh logic het han 24h theo ngay chay test
  const pendingInvite = {
    inviteId: 'i1',
    inviterId: 'alice',
    inviteeId: 'bob',
    inviterMediaUrl: 'https://res.cloudinary.com/x/left.jpg',
    status: 'PENDING' as const,
    createdAt: new Date().toISOString(),
  };

  /** Loi moi tao tu 25 gio truoc — da qua han 24h. */
  const expiredInvite = {
    ...pendingInvite,
    inviteId: 'i2',
    createdAt: new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString(),
  };

  beforeEach(() => {
    repo = {
      create: jest.fn(),
      findById: jest.fn(),
      listPendingForInvitee: jest.fn().mockResolvedValue([]),
      update: jest.fn().mockResolvedValue(undefined),
      markCompletedIfPending: jest.fn().mockResolvedValue(true),
      markDeclinedIfPending: jest.fn().mockResolvedValue(true),
    } as unknown as jest.Mocked<CoopRepository>;
    momentsService = {
      create: jest.fn().mockResolvedValue({ momentId: 'm1', userId: 'alice' }),
    } as unknown as jest.Mocked<MomentsService>;
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
      registerActivityForStreak: jest.fn().mockResolvedValue(1),
      pushToUids: jest.fn().mockResolvedValue(0),
    } as unknown as jest.Mocked<UsersService>;
    questsService = {
      registerMomentPosted: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<QuestsService>;
    framesService = {
      unlockCoopFrames: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FramesService>;
    uploadService = {
      uploadBuffer: jest.fn().mockResolvedValue({ url: 'https://cdn/merged.jpg' }),
    } as unknown as jest.Mocked<UploadService>;

    service = new CoopService(
      repo,
      momentsService,
      friendshipsRepo,
      friendshipsService,
      usersRepo,
      usersService,
      questsService,
      framesService,
      uploadService,
    );
  });

  describe('createInvite', () => {
    const dto = { friendUid: 'bob', mediaUrl: 'https://cdn/left.jpg' };

    it('chan moi chinh minh', async () => {
      await expect(service.createInvite('bob', dto)).rejects.toThrow(BadRequestException);
    });

    it('chan moi nguoi KHONG phai ban be', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null as never);
      await expect(service.createInvite('alice', dto)).rejects.toThrow(ForbiddenException);
    });

    it('tao loi moi PENDING cho ban be', async () => {
      repo.create.mockResolvedValue(pendingInvite as never);

      const result = await service.createInvite('alice', dto);

      expect(repo.create).toHaveBeenCalledWith(
        expect.objectContaining({
          inviterId: 'alice',
          inviteeId: 'bob',
          inviterMediaUrl: dto.mediaUrl,
          status: 'PENDING',
        }),
      );
      expect(result.inviteId).toBe('i1');
    });
  });

  describe('accept', () => {
    const dto = { mediaUrl: 'https://cdn/right.jpg' };

    function mockMerge() {
      return jest
        .spyOn(
          service as unknown as { mergeSideBySide(l: string, r: string): Promise<Buffer> },
          'mergeSideBySide',
        )
        .mockResolvedValue(Buffer.from('merged'));
    }

    it('chan nguoi khong phai invitee', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      await expect(service.accept('stranger', 'i1', dto)).rejects.toThrow(ForbiddenException);
    });

    it('chan loi moi da xu ly roi', async () => {
      repo.findById.mockResolvedValue({ ...pendingInvite, status: 'COMPLETED' } as never);
      await expect(service.accept('bob', 'i1', dto)).rejects.toThrow(BadRequestException);
    });

    it('chan loi moi da HET HAN (qua 24h)', async () => {
      repo.findById.mockResolvedValue(expiredInvite as never);
      await expect(service.accept('bob', 'i2', dto)).rejects.toThrow(BadRequestException);
    });

    it('ghep anh -> upload -> tao moment chung boi nguoi moi + coopUserId', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      const mergeSpy = mockMerge();

      const moment = await service.accept('bob', 'i1', dto);

      // Khoa PENDING->COMPLETED bang transaction TRUOC khi ghep (chong double-accept)
      expect(repo.markCompletedIfPending).toHaveBeenCalledWith('i1');
      expect(mergeSpy).toHaveBeenCalledWith(pendingInvite.inviterMediaUrl, dto.mediaUrl);
      expect(uploadService.uploadBuffer).toHaveBeenCalled();
      expect(momentsService.create).toHaveBeenCalledWith(
        expect.objectContaining({ uid: 'alice' }),
        expect.objectContaining({ contentType: 'PHOTO', mediaUrl: 'https://cdn/merged.jpg' }),
        'bob',
      );
      expect(repo.update).toHaveBeenCalledWith('i1', expect.objectContaining({ momentId: 'm1' }));
      // Nguoi nhan cung duoc tinh streak + quest + friend streak
      expect(usersService.registerActivityForStreak).toHaveBeenCalledWith('bob');
      expect(questsService.registerMomentPosted).toHaveBeenCalledWith('bob', 1);
      expect(friendshipsService.registerInteraction).toHaveBeenCalledWith('bob', 'alice');
      expect(moment.momentId).toBe('m1');
    });

    it('chan accept THU 2 dong thoi (transaction lock tra ve false)', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      repo.markCompletedIfPending.mockResolvedValue(false as never);
      await expect(service.accept('bob', 'i1', dto)).rejects.toThrow(BadRequestException);
      expect(momentsService.create).not.toHaveBeenCalled();
    });

    it('ghep anh FAIL -> tra loi moi ve PENDING de thu lai', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      jest
        .spyOn(
          service as unknown as { mergeSideBySide(l: string, r: string): Promise<Buffer> },
          'mergeSideBySide',
        )
        .mockRejectedValue(new BadRequestException('Khong tai duoc anh de ghep.'));

      await expect(service.accept('bob', 'i1', dto)).rejects.toThrow(BadRequestException);
      expect(repo.update).toHaveBeenCalledWith('i1', { status: 'PENDING' });
      expect(momentsService.create).not.toHaveBeenCalled();
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
    it('danh dau DECLINED qua transaction', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);

      await service.decline('bob', 'i1');

      expect(repo.markDeclinedIfPending).toHaveBeenCalledWith('i1');
    });

    it('chan decline khi accept vua khoa loi moi (transition false)', async () => {
      repo.findById.mockResolvedValue(pendingInvite as never);
      repo.markDeclinedIfPending.mockResolvedValue(false as never);

      await expect(service.decline('bob', 'i1')).rejects.toThrow(BadRequestException);
    });
  });
});
