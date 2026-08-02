import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { MAX_GROUP_SIZE } from '../common/constants';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { PaginatedResult, PaginationDto } from '../common/dto/pagination.dto';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { AddGroupMembersDto } from './dto/add-group-members.dto';
import { CreateGroupDto } from './dto/create-group.dto';
import { SendMessageDto } from './dto/send-message.dto';
import { UpdateGroupDto } from './dto/update-group.dto';
import {
  ChatGroup,
  ChatGroupDetail,
  ConversationSummary,
  Message,
} from './entities/message.entity';
import { MessagesRepository } from './messages.repository';

@Injectable()
export class MessagesService {
  private readonly logger = new Logger(MessagesService.name);

  constructor(
    private readonly repo: MessagesRepository,
    private readonly usersRepo: UsersRepository,
    private readonly usersService: UsersService,
    private readonly friendshipsService: FriendshipsService,
    private readonly friendshipsRepo: FriendshipsRepository,
  ) {}

  /**
   * Gui tin nhan: receiverId (1-1, phai la ban be) HOAC groupId (phai la thanh vien).
   * 1-1: wire friend streak + FCM nguoi nhan. Nhom: FCM cac thanh vien khac.
   */
  async send(authUser: AuthUser, dto: SendMessageDto): Promise<Message> {
    const hasReceiver = !!dto.receiverId;
    const hasGroup = !!dto.groupId;
    if (hasReceiver === hasGroup) {
      throw new BadRequestException('Dien dung 1 trong 2: receiverId (1-1) hoac groupId (nhom).');
    }

    if (hasReceiver) {
      return this.sendDirect(authUser, dto);
    }
    return this.sendToGroup(authUser, dto);
  }

  private async sendDirect(authUser: AuthUser, dto: SendMessageDto): Promise<Message> {
    const receiverId = dto.receiverId as string;
    if (receiverId === authUser.uid) {
      throw new BadRequestException('Khong the tu nhan tin cho chinh minh.');
    }
    const pair = await this.friendshipsRepo.findPair(authUser.uid, receiverId);
    if (!pair || pair.status !== 'ACCEPTED') {
      throw new ForbiddenException('Chi nhan tin duoc voi ban be.');
    }

    const replyFields = await this.resolveReply(dto.replyToId, (original) => {
      const between = [original.senderId, original.receiverId];
      return !original.groupId && between.includes(authUser.uid) && between.includes(receiverId);
    });

    const message = await this.repo.createMessage({
      senderId: authUser.uid,
      receiverId,
      messageType: dto.messageType,
      content: dto.content,
      sendTime: new Date().toISOString(),
      isSeen: false,
      attachmentUrl: dto.attachmentUrl,
      attachmentType: dto.attachmentType,
      ...replyFields,
    });

    // Nhan tin = tuong tac qua lai -> cap nhat friend streak.
    await this.friendshipsService.registerInteraction(authUser.uid, receiverId).catch((e) => {
      this.logger.warn(`Khong cap nhat duoc friend streak: ${e.message}`);
    });

    await this.pushTo([receiverId], authUser.uid, dto).catch((e) => {
      this.logger.warn(`Khong gui duoc FCM: ${e.message}`);
    });

    return message;
  }

  private async sendToGroup(authUser: AuthUser, dto: SendMessageDto): Promise<Message> {
    const groupId = dto.groupId as string;
    const group = await this.requireMembership(groupId, authUser.uid);

    const replyFields = await this.resolveReply(
      dto.replyToId,
      (original) => original.groupId === groupId,
    );

    const message = await this.repo.createMessage({
      senderId: authUser.uid,
      groupId,
      messageType: dto.messageType,
      content: dto.content,
      sendTime: new Date().toISOString(),
      isSeen: false,
      attachmentUrl: dto.attachmentUrl,
      attachmentType: dto.attachmentType,
      ...replyFields,
    });

    // Bo qua thanh vien da tat thong bao nhom (mutedBy) khi gui FCM.
    const muted = new Set(group.mutedBy ?? []);
    const others = group.memberIds.filter((id) => id !== authUser.uid && !muted.has(id));
    await this.pushTo(others, authUser.uid, dto).catch((e) => {
      this.logger.warn(`Khong gui duoc FCM nhom: ${e.message}`);
    });

    return message;
  }

  /** Thread 1-1 voi 1 nguoi ban (cu -> moi, phan trang tu cuoi). */
  async getThread(
    uid: string,
    friendUid: string,
    pagination: PaginationDto,
  ): Promise<PaginatedResult<Message>> {
    const all = await this.repo.listBetween(uid, friendUid);
    return this.paginateLatest(all, pagination);
  }

  /** Thread nhom (phai la thanh vien). */
  async getGroupThread(
    uid: string,
    groupId: string,
    pagination: PaginationDto,
  ): Promise<PaginatedResult<Message>> {
    await this.requireMembership(groupId, uid);
    const all = await this.repo.listByGroup(groupId);
    return this.paginateLatest(all, pagination);
  }

  /** Danh sach hoi thoai 1-1: tin nhan moi nhat voi tung nguoi. */
  async getConversations(uid: string): Promise<ConversationSummary[]> {
    const all = await this.repo.listInvolving(uid);
    const byCounterpart = new Map<string, Message>();
    for (const msg of all) {
      if (msg.groupId) {
        continue;
      }
      const counterpart = msg.senderId === uid ? (msg.receiverId ?? '') : msg.senderId;
      if (!counterpart) {
        continue;
      }
      const current = byCounterpart.get(counterpart);
      if (!current || msg.sendTime > current.sendTime) {
        byCounterpart.set(counterpart, msg);
      }
    }
    return [...byCounterpart.entries()]
      .map(([counterpartId, lastMessage]) => ({ counterpartId, lastMessage }))
      .sort((a, b) => b.lastMessage.sendTime.localeCompare(a.lastMessage.sendTime));
  }

  /**
   * Xu ly reply (kieu Messenger): [replyToId] rong -> khong reply; co -> tin goc
   * phai ton tai va thoa [belongsToConversation] (cung hoi thoai 1-1 / cung nhom).
   * Tra ve snapshot type/content/senderId cua tin goc de luu vao tin moi
   * (app ve khoi trich dan khong can lookup lai).
   */
  private async resolveReply(
    replyToId: string | undefined,
    belongsToConversation: (original: Message) => boolean,
  ): Promise<Partial<Message>> {
    if (!replyToId) {
      return {};
    }
    const original = await this.repo.findById(replyToId);
    if (!original || !belongsToConversation(original)) {
      throw new BadRequestException('Tin nhan duoc reply khong nam trong hoi thoai nay.');
    }
    return {
      replyToId,
      replyToType: original.messageType,
      replyToContent: original.content,
      replyToSenderId: original.senderId,
    };
  }

  /**
   * Tha reaction len tin nhan — chi nguoi TRONG hoi thoai (1-1: sender/receiver;
   * nhom: thanh vien). Moi nguoi 1 reaction; bam lai cung emoji = go (toggle).
   * Tra ve tin nhan da cap nhat de app thay reactions ngay khong cho poll.
   */
  async react(uid: string, messageId: string, emoji: string): Promise<Message> {
    const message = await this.repo.findById(messageId);
    if (!message) {
      throw new NotFoundException('Khong tim thay tin nhan.');
    }

    if (message.groupId) {
      const group = await this.repo.findGroup(message.groupId);
      if (!group || !group.memberIds.includes(uid)) {
        throw new ForbiddenException('Ban khong phai thanh vien nhom nay.');
      }
    } else if (message.senderId !== uid && message.receiverId !== uid) {
      throw new ForbiddenException('Chi nguoi trong hoi thoai moi tha reaction duoc.');
    }

    const current = message.reactions?.[uid];
    const next = current === emoji ? null : emoji;
    await this.repo.setReaction(messageId, uid, next);

    const reactions = { ...(message.reactions ?? {}) };
    if (next === null) {
      delete reactions[uid];
    } else {
      reactions[uid] = next;
    }
    return { ...message, reactions };
  }

  /** Danh dau da xem — chi nguoi nhan lam duoc. */
  async markSeen(uid: string, messageId: string): Promise<void> {
    const message = await this.repo.findById(messageId);
    if (!message) {
      throw new NotFoundException('Khong tim thay tin nhan.');
    }
    if (message.receiverId !== uid) {
      throw new ForbiddenException('Chi nguoi nhan moi danh dau da xem duoc.');
    }
    await this.repo.markSeen(messageId);
  }

  /**
   * Tao nhom chat: nguoi tao tu dong vao nhom; tong thanh vien <= 20.
   * Fix 2026-07-26: moi thanh vien PHAI la ban be cua nguoi tao — khong co check
   * nay thi biet uid nguoi la la tao duoc nhom 2 nguoi, lach luat "chi nhan tin
   * voi ban be" cua tin 1-1.
   */
  async createGroup(uid: string, dto: CreateGroupDto): Promise<ChatGroup> {
    const memberIds = [...new Set([uid, ...dto.memberIds])];
    if (memberIds.length > MAX_GROUP_SIZE) {
      throw new BadRequestException(`Nhom chat toi da ${MAX_GROUP_SIZE} thanh vien.`);
    }
    await this.assertAllFriendsOf(uid, memberIds);

    return this.repo.createGroup({
      groupName: dto.groupName,
      memberIds,
      createdBy: uid,
      createdAt: new Date().toISOString(),
    });
  }

  async listMyGroups(uid: string): Promise<ChatGroup[]> {
    return this.repo.listGroupsByMember(uid);
  }

  /** Chi tiet nhom + ho so cong khai tung thanh vien (phai la thanh vien moi xem duoc). */
  async getGroupDetail(uid: string, groupId: string): Promise<ChatGroupDetail> {
    const group = await this.requireMembership(groupId, uid);
    const members = await Promise.all(
      group.memberIds.map(async (memberUid) => {
        const profile = await this.usersService.getPublicProfile(memberUid).catch(() => null);
        return {
          uid: memberUid,
          fullName: profile?.fullName ?? 'Nguoi dung',
          avatar: profile?.avatar,
        };
      }),
    );
    return { ...group, members };
  }

  /** Doi ten / anh dai dien nhom — moi thanh vien deu doi duoc (kieu Messenger). */
  async updateGroup(uid: string, groupId: string, dto: UpdateGroupDto): Promise<ChatGroup> {
    const group = await this.requireMembership(groupId, uid);
    if (dto.groupName === undefined && dto.avatar === undefined) {
      throw new BadRequestException('Can it nhat 1 truong: groupName hoac avatar.');
    }
    await this.repo.updateGroup(groupId, { groupName: dto.groupName, avatar: dto.avatar });
    return {
      ...group,
      groupName: dto.groupName ?? group.groupName,
      avatar: dto.avatar ?? group.avatar,
    };
  }

  /**
   * Them thanh vien: moi thanh vien nhom deu moi duoc, nhung nguoi duoc moi PHAI
   * la ban be cua NGUOI MOI (giu nguyen rao chan "chi ban be" nhu createGroup —
   * khong co check nay la lach duoc luat nhan tin voi nguoi la).
   */
  async addMembers(uid: string, groupId: string, dto: AddGroupMembersDto): Promise<ChatGroup> {
    const group = await this.requireMembership(groupId, uid);
    const newcomers = [...new Set(dto.memberIds)].filter((id) => !group.memberIds.includes(id));
    if (newcomers.length === 0) {
      throw new BadRequestException('Nhung nguoi nay da o trong nhom.');
    }
    const memberIds = [...group.memberIds, ...newcomers];
    if (memberIds.length > MAX_GROUP_SIZE) {
      throw new BadRequestException(`Nhom chat toi da ${MAX_GROUP_SIZE} thanh vien.`);
    }
    await this.assertAllFriendsOf(uid, newcomers);
    await this.repo.updateGroup(groupId, { memberIds });
    return { ...group, memberIds };
  }

  /** Xoa thanh vien khoi nhom — CHI nguoi tao nhom (createdBy) lam duoc. */
  async removeMember(uid: string, groupId: string, memberUid: string): Promise<ChatGroup> {
    const group = await this.requireMembership(groupId, uid);
    if (group.createdBy !== uid) {
      throw new ForbiddenException('Chi nguoi tao nhom moi xoa duoc thanh vien.');
    }
    if (memberUid === uid) {
      throw new BadRequestException('Muon roi nhom hay dung chuc nang roi nhom.');
    }
    if (!group.memberIds.includes(memberUid)) {
      throw new NotFoundException('Nguoi nay khong o trong nhom.');
    }
    const memberIds = group.memberIds.filter((id) => id !== memberUid);
    const mutedBy = (group.mutedBy ?? []).filter((id) => id !== memberUid);
    await this.repo.updateGroup(groupId, { memberIds, mutedBy });
    return { ...group, memberIds, mutedBy };
  }

  /**
   * Roi nhom: thanh vien cuoi cung roi -> xoa nhom; nguoi tao roi -> chuyen
   * quyen createdBy cho thanh vien con lai dau tien (nhom luon co nguoi quan ly).
   */
  async leaveGroup(uid: string, groupId: string): Promise<void> {
    const group = await this.requireMembership(groupId, uid);
    const memberIds = group.memberIds.filter((id) => id !== uid);
    if (memberIds.length === 0) {
      await this.repo.deleteGroup(groupId);
      return;
    }
    const mutedBy = (group.mutedBy ?? []).filter((id) => id !== uid);
    const createdBy = group.createdBy === uid ? memberIds[0] : group.createdBy;
    await this.repo.updateGroup(groupId, { memberIds, mutedBy, createdBy });
  }

  /** Bat/tat thong bao nhom cho rieng minh (mutedBy) — khong dung toi thanh vien khac. */
  async setGroupMuted(uid: string, groupId: string, muted: boolean): Promise<ChatGroup> {
    const group = await this.requireMembership(groupId, uid);
    const current = new Set(group.mutedBy ?? []);
    if (muted) {
      current.add(uid);
    } else {
      current.delete(uid);
    }
    const mutedBy = [...current];
    await this.repo.updateGroup(groupId, { mutedBy });
    return { ...group, mutedBy };
  }

  /** Nhom phai ton tai va uid phai la thanh vien — tra ve nhom de dung tiep. */
  private async requireMembership(groupId: string, uid: string): Promise<ChatGroup> {
    const group = await this.repo.findGroup(groupId);
    if (!group) {
      throw new NotFoundException('Khong tim thay nhom chat.');
    }
    if (!group.memberIds.includes(uid)) {
      throw new ForbiddenException('Ban khong phai thanh vien nhom nay.');
    }
    return group;
  }

  /**
   * Moi uid trong [candidateIds] (tru chinh minh) phai la ban be ACCEPTED cua [uid].
   * Fix 2026-07-26 (createGroup) — ap dung cho ca addMembers de khong mo lai lo hong.
   */
  private async assertAllFriendsOf(uid: string, candidateIds: string[]): Promise<void> {
    const friendships = await this.friendshipsRepo.listAccepted(uid);
    const friendIds = new Set(friendships.map((f) => f.userIds.find((id) => id !== uid) ?? ''));
    const strangers = candidateIds.filter((id) => id !== uid && !friendIds.has(id));
    if (strangers.length > 0) {
      throw new ForbiddenException('Chi them duoc ban be vao nhom chat.');
    }
  }

  /** Trang cuoi = tin moi nhat: tra ve doan cuoi cua thread theo page/limit. */
  private paginateLatest(all: Message[], { page, limit }: PaginationDto): PaginatedResult<Message> {
    const start = Math.max(0, all.length - page * limit);
    const end = all.length - (page - 1) * limit;
    return {
      items: end > 0 ? all.slice(start, end) : [],
      page,
      limit,
      total: all.length,
    };
  }

  /** Bao tin nhan moi cho danh sach uid (helper push chung o UsersService). */
  private async pushTo(uids: string[], senderUid: string, dto: SendMessageDto): Promise<void> {
    if (uids.length === 0) {
      return;
    }
    const sender = await this.usersRepo.findByUid(senderUid);
    const preview = dto.messageType === 'TEXT' ? dto.content.slice(0, 80) : this.previewOf(dto);
    await this.usersService.pushToUids(uids, sender?.fullName ?? 'Tin nhan moi', preview);
  }

  private previewOf(dto: SendMessageDto): string {
    switch (dto.messageType) {
      case 'VOICE':
        return '[Tin nhan thoai]';
      case 'STICKER':
        return '[Sticker]';
      case 'PHOTO':
        return '[Hinh anh]';
      default:
        return dto.content.slice(0, 80);
    }
  }
}
