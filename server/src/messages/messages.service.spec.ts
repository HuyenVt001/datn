import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { MAX_GROUP_SIZE } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { SendMessageDto } from './dto/send-message.dto';
import { MessagesRepository } from './messages.repository';
import { MessagesService } from './messages.service';

describe('MessagesService', () => {
  let service: MessagesService;
  let repo: jest.Mocked<MessagesRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let usersService: jest.Mocked<UsersService>;
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
      setReaction: jest.fn().mockResolvedValue(undefined),
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
    friendshipsRepo = {
      findPair: jest.fn(),
      listAccepted: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FriendshipsRepository>;

    usersService = {
      pushToUids: jest.fn().mockResolvedValue(0),
    } as unknown as jest.Mocked<UsersService>;

    service = new MessagesService(
      repo,
      usersRepo,
      usersService,
      friendshipsService,
      friendshipsRepo,
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

    it('reply 1-1: snapshot type/content/sender cua tin goc vao tin moi', async () => {
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);
      repo.findById.mockResolvedValue({
        messageId: 'orig',
        senderId: 'friend',
        receiverId: 'me',
        messageType: 'PHOTO',
        content: 'https://img.example/a.jpg',
      } as never);

      await service.send(me, textDto({ receiverId: 'friend', replyToId: 'orig' }));

      expect(repo.createMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          replyToId: 'orig',
          replyToType: 'PHOTO',
          replyToContent: 'https://img.example/a.jpg',
          replyToSenderId: 'friend',
        }),
      );
    });

    it('chan reply tin KHONG nam trong hoi thoai (cua cap khac / cua nhom)', async () => {
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);
      repo.findById.mockResolvedValue({
        messageId: 'orig',
        senderId: 'x',
        receiverId: 'y',
      } as never);

      await expect(
        service.send(me, textDto({ receiverId: 'friend', replyToId: 'orig' })),
      ).rejects.toThrow(BadRequestException);
      expect(repo.createMessage).not.toHaveBeenCalled();
    });

    it('reply trong nhom: tin goc phai thuoc CUNG nhom', async () => {
      repo.findGroup.mockResolvedValue({ groupId: 'g', memberIds: ['me', 'a'] } as never);
      repo.findById.mockResolvedValue({
        messageId: 'orig',
        senderId: 'a',
        groupId: 'OTHER_GROUP',
      } as never);

      await expect(service.send(me, textDto({ groupId: 'g', replyToId: 'orig' }))).rejects.toThrow(
        BadRequestException,
      );
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
      friendshipsRepo.listAccepted.mockResolvedValue([
        { userIds: ['me', 'a'], status: 'ACCEPTED' },
      ] as never);

      await service.createGroup('me', { groupName: 'g', memberIds: ['a', 'a', 'me'] });

      expect(repo.createGroup).toHaveBeenCalledWith(
        expect.objectContaining({ memberIds: ['me', 'a'], createdBy: 'me' }),
      );
    });

    it('chan them NGUOI LA vao nhom (chi ban be)', async () => {
      friendshipsRepo.listAccepted.mockResolvedValue([
        { userIds: ['me', 'a'], status: 'ACCEPTED' },
      ] as never);

      await expect(
        service.createGroup('me', { groupName: 'g', memberIds: ['a', 'stranger'] }),
      ).rejects.toThrow(ForbiddenException);
      expect(repo.createGroup).not.toHaveBeenCalled();
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

  describe('react', () => {
    it('chan nguoi ngoai hoi thoai 1-1 tha reaction', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', senderId: 'a', receiverId: 'b' } as never);
      await expect(service.react('me', 'm', '💛')).rejects.toThrow(ForbiddenException);
    });

    it('nguoi trong hoi thoai tha reaction thanh cong', async () => {
      repo.findById.mockResolvedValue({
        messageId: 'm',
        senderId: 'friend',
        receiverId: 'me',
        reactions: {},
      } as never);

      const result = await service.react('me', 'm', '💛');

      expect(repo.setReaction).toHaveBeenCalledWith('m', 'me', '💛');
      expect(result.reactions).toEqual({ me: '💛' });
    });

    it('bam lai cung emoji = go reaction (toggle)', async () => {
      repo.findById.mockResolvedValue({
        messageId: 'm',
        senderId: 'friend',
        receiverId: 'me',
        reactions: { me: '💛' },
      } as never);

      const result = await service.react('me', 'm', '💛');

      expect(repo.setReaction).toHaveBeenCalledWith('m', 'me', null);
      expect(result.reactions).toEqual({});
    });

    it('tin nhom: chi thanh vien nhom moi tha duoc', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', senderId: 'a', groupId: 'g' } as never);
      repo.findGroup.mockResolvedValue({ groupId: 'g', memberIds: ['a', 'b'] } as never);
      await expect(service.react('me', 'm', '💛')).rejects.toThrow(ForbiddenException);
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
