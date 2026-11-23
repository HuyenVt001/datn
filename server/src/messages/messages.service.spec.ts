import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { MAX_GROUP_SIZE } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FriendshipsRepository } from '../friendships/friendships.repository';
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
      updateGroup: jest.fn().mockResolvedValue(undefined),
      deleteGroup: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<MessagesRepository>;
    usersRepo = {
      findByUid: jest.fn().mockResolvedValue(null),
    } as unknown as jest.Mocked<UsersRepository>;
    friendshipsRepo = {
      findPair: jest.fn(),
      listAccepted: jest.fn().mockResolvedValue([]),
    } as unknown as jest.Mocked<FriendshipsRepository>;

    usersService = {
      pushToUids: jest.fn().mockResolvedValue(0),
      getPublicProfile: jest.fn().mockResolvedValue({ uid: 'x', fullName: 'X' }),
    } as unknown as jest.Mocked<UsersService>;

    service = new MessagesService(repo, usersRepo, usersService, friendshipsRepo);
  });

  describe('send', () => {
    it('error when both receiverId and groupId are set (or neither)', async () => {
      await expect(service.send(me, textDto({}))).rejects.toThrow(BadRequestException);
      await expect(service.send(me, textDto({ receiverId: 'a', groupId: 'g' }))).rejects.toThrow(
        BadRequestException,
      );
    });

    it('blocks 1-1 messages when not friends', async () => {
      friendshipsRepo.findPair.mockResolvedValue(null);
      await expect(service.send(me, textDto({ receiverId: 'stranger' }))).rejects.toThrow(
        ForbiddenException,
      );
    });

    it('1-1 send succeeds + wires friend streak', async () => {
      friendshipsRepo.findPair.mockResolvedValue({ status: 'ACCEPTED' } as never);

      const result = await service.send(me, textDto({ receiverId: 'friend' }));

      expect(repo.createMessage).toHaveBeenCalledWith(
        expect.objectContaining({ senderId: 'me', receiverId: 'friend', isSeen: false }),
      );
      expect(result.messageId).toBe('msg1');
    });

    it("blocks sending to a group you're not in", async () => {
      repo.findGroup.mockResolvedValue({ groupId: 'g', memberIds: ['a', 'b'] } as never);
      await expect(service.send(me, textDto({ groupId: 'g' }))).rejects.toThrow(ForbiddenException);
    });

    it('reply 1-1: snapshots type/content/sender of the original into the new message', async () => {
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

    it('blocks replying to a message NOT in the conversation (another pair / a group)', async () => {
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

    it('reply in group: the original must belong to the SAME group', async () => {
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
    it(`blocks groups over ${MAX_GROUP_SIZE} members`, async () => {
      const tooMany = Array.from({ length: MAX_GROUP_SIZE }, (_, i) => `u${i}`);
      await expect(
        service.createGroup('me', { groupName: 'g', memberIds: tooMany }),
      ).rejects.toThrow(BadRequestException);
    });

    it('creator joins automatically + duplicates removed', async () => {
      repo.createGroup.mockResolvedValue({ groupId: 'g1' } as never);
      friendshipsRepo.listAccepted.mockResolvedValue([
        { userIds: ['me', 'a'], status: 'ACCEPTED' },
      ] as never);

      await service.createGroup('me', { groupName: 'g', memberIds: ['a', 'a', 'me'] });

      expect(repo.createGroup).toHaveBeenCalledWith(
        expect.objectContaining({ memberIds: ['me', 'a'], createdBy: 'me' }),
      );
    });

    it('blocks adding STRANGERS to a group (friends only)', async () => {
      friendshipsRepo.listAccepted.mockResolvedValue([
        { userIds: ['me', 'a'], status: 'ACCEPTED' },
      ] as never);

      await expect(
        service.createGroup('me', { groupName: 'g', memberIds: ['a', 'stranger'] }),
      ).rejects.toThrow(ForbiddenException);
      expect(repo.createGroup).not.toHaveBeenCalled();
    });
  });

  describe('group management', () => {
    const group = () => ({
      groupId: 'g',
      groupName: 'Squad',
      memberIds: ['creator', 'me', 'other'],
      createdBy: 'creator',
      createdAt: '2026-01-01',
      mutedBy: [] as string[],
    });

    beforeEach(() => {
      repo.findGroup.mockResolvedValue(group() as never);
    });

    it('getGroupDetail: blocks non-members', async () => {
      await expect(service.getGroupDetail('outsider', 'g')).rejects.toThrow(ForbiddenException);
    });

    it("getGroupDetail: returns group + each member's public profile", async () => {
      usersService.getPublicProfile.mockImplementation(
        async (uid: string) => ({ uid, fullName: `name-${uid}` }) as never,
      );

      const result = await service.getGroupDetail('me', 'g');

      expect(result.members).toHaveLength(3);
      expect(result.members[1]).toEqual({ uid: 'me', fullName: 'name-me', avatar: undefined });
    });

    it('updateGroup: renames/changes avatar, errors on empty body', async () => {
      const updated = await service.updateGroup('me', 'g', { groupName: 'New name' });

      expect(repo.updateGroup).toHaveBeenCalledWith('g', {
        groupName: 'New name',
        avatar: undefined,
      });
      expect(updated.groupName).toBe('New name');
      await expect(service.updateGroup('me', 'g', {})).rejects.toThrow(BadRequestException);
    });

    it("addMembers: can only add the inviter's FRIENDS", async () => {
      friendshipsRepo.listAccepted.mockResolvedValue([
        { userIds: ['me', 'friend'], status: 'ACCEPTED' },
      ] as never);

      await expect(service.addMembers('me', 'g', { memberIds: ['stranger'] })).rejects.toThrow(
        ForbiddenException,
      );
      expect(repo.updateGroup).not.toHaveBeenCalled();
    });

    it('addMembers: adds friends, skips people already in the group', async () => {
      friendshipsRepo.listAccepted.mockResolvedValue([
        { userIds: ['me', 'friend'], status: 'ACCEPTED' },
      ] as never);

      const updated = await service.addMembers('me', 'g', { memberIds: ['friend', 'other'] });

      expect(updated.memberIds).toEqual(['creator', 'me', 'other', 'friend']);
      await expect(service.addMembers('me', 'g', { memberIds: ['other'] })).rejects.toThrow(
        BadRequestException,
      );
    });

    it(`addMembers: blocks groups exceeding ${MAX_GROUP_SIZE} members`, async () => {
      const full = group();
      full.memberIds = Array.from({ length: MAX_GROUP_SIZE }, (_, i) => (i === 0 ? 'me' : `u${i}`));
      repo.findGroup.mockResolvedValue(full as never);

      await expect(service.addMembers('me', 'g', { memberIds: ['one-more'] })).rejects.toThrow(
        BadRequestException,
      );
    });

    it('removeMember: only the group CREATOR can remove members', async () => {
      await expect(service.removeMember('me', 'g', 'other')).rejects.toThrow(ForbiddenException);

      const updated = await service.removeMember('creator', 'g', 'other');
      expect(updated.memberIds).toEqual(['creator', 'me']);
    });

    it('removeMember: creator cannot remove themselves (must leave the group)', async () => {
      await expect(service.removeMember('creator', 'g', 'creator')).rejects.toThrow(
        BadRequestException,
      );
    });

    it('leaveGroup: a regular member leaves', async () => {
      await service.leaveGroup('me', 'g');
      expect(repo.updateGroup).toHaveBeenCalledWith('g', {
        memberIds: ['creator', 'other'],
        mutedBy: [],
        createdBy: 'creator',
      });
    });

    it('leaveGroup: creator leaves -> ownership passes to the first remaining member', async () => {
      await service.leaveGroup('creator', 'g');
      expect(repo.updateGroup).toHaveBeenCalledWith(
        'g',
        expect.objectContaining({ createdBy: 'me' }),
      );
    });

    it('leaveGroup: last member leaves -> group deleted', async () => {
      const solo = group();
      solo.memberIds = ['me'];
      repo.findGroup.mockResolvedValue(solo as never);

      await service.leaveGroup('me', 'g');

      expect(repo.deleteGroup).toHaveBeenCalledWith('g');
      expect(repo.updateGroup).not.toHaveBeenCalled();
    });

    it('setGroupMuted: mute/unmute for yourself only', async () => {
      const muted = await service.setGroupMuted('me', 'g', true);
      expect(muted.mutedBy).toEqual(['me']);

      const already = group();
      already.mutedBy = ['me', 'other'];
      repo.findGroup.mockResolvedValue(already as never);
      const unmuted = await service.setGroupMuted('me', 'g', false);
      expect(unmuted.mutedBy).toEqual(['other']);
    });

    it('group send: NO FCM push to muted members', async () => {
      const withMuted = group();
      withMuted.memberIds = ['me', 'a', 'b'];
      withMuted.mutedBy = ['b'];
      repo.findGroup.mockResolvedValue(withMuted as never);

      await service.send(me, textDto({ groupId: 'g' }));

      expect(usersService.pushToUids).toHaveBeenCalledWith(['a'], expect.any(String), 'hi');
    });
  });

  describe('markSeen', () => {
    it('only the recipient can mark as seen', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', receiverId: 'someone-else' } as never);
      await expect(service.markSeen('me', 'm')).rejects.toThrow(ForbiddenException);
    });

    it('recipient marks as seen successfully', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', receiverId: 'me' } as never);
      await service.markSeen('me', 'm');
      expect(repo.markSeen).toHaveBeenCalledWith('m');
    });
  });

  describe('react', () => {
    it('blocks outsiders from reacting in a 1-1 conversation', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', senderId: 'a', receiverId: 'b' } as never);
      await expect(service.react('me', 'm', '💛')).rejects.toThrow(ForbiddenException);
    });

    it('conversation member reacts successfully', async () => {
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

    it('same emoji again = remove reaction (toggle)', async () => {
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

    it('group message: only group members can react', async () => {
      repo.findById.mockResolvedValue({ messageId: 'm', senderId: 'a', groupId: 'g' } as never);
      repo.findGroup.mockResolvedValue({ groupId: 'g', memberIds: ['a', 'b'] } as never);
      await expect(service.react('me', 'm', '💛')).rejects.toThrow(ForbiddenException);
    });
  });

  describe('getConversations', () => {
    it('gets the latest message with each person, skips group messages', async () => {
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
