import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { MAX_GROUP_SIZE } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FirebaseService } from '../firebase/firebase.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { UsersRepository } from '../users/users.repository';
import { SendMessageDto } from './dto/send-message.dto';
import { MessagesRepository } from './messages.repository';
import { MessagesService } from './messages.service';

describe('MessagesService', () => {
  let service: MessagesService;
  let repo: jest.Mocked<MessagesRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let friendshipsService: jest.Mocked<FriendshipsService>;
  let friendshipsRepo: jest.Mocked<FriendshipsRepository>;

  const me: AuthUser = { uid: 'me' };
  const textDto = (extra: Partial<SendMessageDto>): SendMessageDto =>
    ({ messageType: 'TEXT', content: 'hi', ...extra }) as SendMessageDto;

  beforeEach(() => {
    repo = {
      createMessage: jest.fn().mockResolvedValue({ messageId: 'msg1' }),
      findById: jest.fn(),
      listBetween: jest.fn(),
      listInvolving: jest.fn(),
      listByGroup: jest.fn(),
      markSeen: jest.fn(),
      createGroup: jest.fn(),
      findGroup: jest.fn(),
      listGroupsByMember: jest.fn(),
    } as unknown as jest.Mocked<MessagesRepository>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<UsersRepository>;
    friendshipsService = {
      registerInteraction: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<FriendshipsService>;
    friendshipsRepo = { findPair: jest.fn() } as unknown as jest.Mocked<FriendshipsRepository>;

    service = new MessagesService(
      repo,
      usersRepo,
      friendshipsService,
      friendshipsRepo,
      {} as FirebaseService,
    );
  });

  describe('send', () => {
    it('bao loi khi dien ca receiverId lan groupId (hoac khong dien gi)', async () => {
      await expect(service.send(me, textDto({}))).rejects.toThrow(BadRequestException);
      await expect(service.send(me, textDto({ receiverId: 'a', groupId: 'g' }))).rejects.toThrow(
        BadRequestException,
      );
    });

    it('chan nhan tin 1-1 khi chua la ban be', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null);
      await expect(service.send(me, textDto({ receiverId: 'stranger' }))).rejects.toThrow(
        ForbiddenException,
      );
    });

    it('gui 1-1 thanh cong + wire friend streak', async () => {
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);

      const result = await service.send(me, textDto({ receiverId: 'friend' }));

      expect(repo.createMessage).toHaveBeenCalledWith(
        expect.objectContaining({ senderId: 'me', receiverId: 'friend', isSeen: false }),
      );
      expect(friendshipsService.registerInteraction).toHaveBeenCalledWith('me', 'friend');
      expect(result.messageId).toBe('msg1');
    });

    it('chan gui vao nhom khong phai thanh vien', async () => {
      repo.findGroup.mockResolvedValue({ groupId: 'g', memberIds: ['a', 'b'] } as never);
      await expect(service.send(me, textDto({ groupId: 'g' }))).rejects.toThrow(ForbiddenException);
    });
  });

  describe('createGroup', () => {
    it(`chan nhom qua ${MAX_GROUP_SIZE} thanh vien`, async () => {
      const tooMany = Array.from({ length: MAX_GROUP_SIZE }, (_, i) => `u${i}`);
      await expect(
        service.createGroup('me', { groupName: 'g', memberIds: tooMany }),
      ).rejects.toThrow(BadRequestException);
    });

    it('nguoi tao tu dong vao nhom + khu trung lap', async () => {
      repo.createGroup.mockResolvedValue({ groupId: 'g1' } as never);

      await service.createGroup('me', { groupName: 'g', memberIds: ['a', 'a', 'me'] });

      expect(repo.createGroup).toHaveBeenCalledWith(
        expect.objectContaining({ memberIds: ['me', 'a'], createdBy: 'me' }),
      );
    });
  });

  describe('markSeen', () => {
    it('chi nguoi nhan moi danh dau duoc', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', receiverId: 'someone-else' } as never);
      await expect(service.markSeen('me', 'm')).rejects.toThrow(ForbiddenException);
    });

    it('nguoi nhan danh dau thanh cong', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', receiverId: 'me' } as never);
      await service.markSeen('me', 'm');
      expect(repo.markSeen).toHaveBeenCalledWith('m');
    });
  });

  describe('getConversations', () => {
    it('lay tin moi nhat voi tung nguoi, bo qua tin nhom', async () => {
      repo.listInvolving.mockResolvedValue([
        {
          messageId: '1',
          senderId: 'me',
          receiverId: 'a',
          sendTime: '2026-01-01',
          groupId: undefined,
        },
        {
          messageId: '2',
          senderId: 'a',
          receiverId: 'me',
          sendTime: '2026-01-02',
          groupId: undefined,
        },
        { messageId: '3', senderId: 'me', groupId: 'g', sendTime: '2026-01-03' },
      ] as never);

      const result = await service.getConversations('me');

      expect(result).toHaveLength(1);
      expect(result[0].counterpartId).toBe('a');
      expect(result[0].lastMessage.messageId).toBe('2');
    });
  });
});
