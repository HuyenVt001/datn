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
import { CreateGroupDto } from './dto/create-group.dto';
import { SendMessageDto } from './dto/send-message.dto';
import { ChatGroup, ConversationSummary, Message } from './entities/message.entity';
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

    const message = await this.repo.createMessage({
      senderId: authUser.uid,
      receiverId,
      messageType: dto.messageType,
      content: dto.content,
      sendTime: new Date().toISOString(),
      isSeen: false,
      attachmentUrl: dto.attachmentUrl,
      attachmentType: dto.attachmentType,
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
    const group = await this.repo.findGroup(groupId);
    if (!group) {
      throw new NotFoundException('Khong tim thay nhom chat.');
    }
    if (!group.memberIds.includes(authUser.uid)) {
      throw new ForbiddenException('Ban khong phai thanh vien nhom nay.');
    }

    const message = await this.repo.createMessage({
      senderId: authUser.uid,
      groupId,
      messageType: dto.messageType,
      content: dto.content,
      sendTime: new Date().toISOString(),
      isSeen: false,
      attachmentUrl: dto.attachmentUrl,
      attachmentType: dto.attachmentType,
    });

    const others = group.memberIds.filter((id) => id !== authUser.uid);
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
    const group = await this.repo.findGroup(groupId);
    if (!group) {
      throw new NotFoundException('Khong tim thay nhom chat.');
    }
    if (!group.memberIds.includes(uid)) {
      throw new ForbiddenException('Ban khong phai thanh vien nhom nay.');
    }
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

    const friendships = await this.friendshipsRepo.listAccepted(uid);
    const friendIds = new Set(friendships.map((f) => f.userIds.find((id) => id !== uid) ?? ''));
    const strangers = memberIds.filter((id) => id !== uid && !friendIds.has(id));
    if (strangers.length > 0) {
      throw new ForbiddenException('Chi them duoc ban be vao nhom chat.');
    }

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
