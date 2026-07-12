import { ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { PaginatedResult, PaginationDto } from '../common/dto/pagination.dto';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FirebaseService } from '../firebase/firebase.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { UsersRepository } from '../users/users.repository';
import { UsersService } from '../users/users.service';
import { CreateMomentDto } from './dto/create-moment.dto';
import { Moment, Reaction } from './entities/moment.entity';
import { MomentsRepository } from './moments.repository';

@Injectable()
export class MomentsService {
  private readonly logger = new Logger(MomentsService.name);

  constructor(
    private readonly repo: MomentsRepository,
    private readonly usersService: UsersService,
    private readonly usersRepo: UsersRepository,
    private readonly friendshipsService: FriendshipsService,
    private readonly friendshipsRepo: FriendshipsRepository,
    private readonly firebase: FirebaseService,
  ) {}

  /**
   * Dang moment: tao doc -> tang personal streak -> bao ban be qua FCM.
   * mediaUrl da qua POST /upload (Cloudinary da enforce video <= 5s).
   */
  async create(authUser: AuthUser, dto: CreateMomentDto): Promise<Moment> {
    const moment = await this.repo.create({
      userId: authUser.uid,
      contentType: dto.contentType,
      mediaUrl: dto.mediaUrl,
      frameId: dto.frameId,
      caption: dto.caption,
      postTime: new Date().toISOString(),
    });

    // Business rule: mo app + upload >= 1 moment/ngay -> tang personal streak.
    await this.usersService.registerActivityForStreak(authUser.uid).catch((e) => {
      this.logger.warn(`Khong cap nhat duoc personal streak: ${e.message}`);
    });

    // Gui push cho danh sach ban be (khong chan luong dang bai neu loi).
    await this.notifyFriends(authUser).catch((e) => {
      this.logger.warn(`Khong gui duoc FCM: ${e.message}`);
    });

    return moment;
  }

  /** Feed = moment cua minh + ban be, sort postTime desc, paginate trong bo nho. */
  async getFeed(uid: string, pagination: PaginationDto): Promise<PaginatedResult<Moment>> {
    const friendships = await this.friendshipsRepo.listAccepted(uid);
    const friendIds = friendships.map((f) => f.userIds.find((id) => id !== uid) ?? '');
    const moments = await this.repo.listByUserIds([uid, ...friendIds]);
    return this.paginate(moments, pagination);
  }

  /** Moment cua chinh minh (profile: calendar + dem tong so bai). */
  async listMine(uid: string, pagination: PaginationDto): Promise<PaginatedResult<Moment>> {
    const moments = await this.repo.listByUserIds([uid]);
    return this.paginate(moments, pagination);
  }

  /** Moment cua 1 user khac — CHI ban be (hoac chinh minh) xem duoc. */
  async listOfUser(
    currentUid: string,
    targetUid: string,
    pagination: PaginationDto,
  ): Promise<PaginatedResult<Moment>> {
    if (targetUid !== currentUid) {
      const pair = await this.friendshipsRepo.findPair(currentUid, targetUid);
      if (!pair || pair.status !== 'ACCEPTED') {
        throw new ForbiddenException('Chi xem duoc moment cua ban be.');
      }
    }
    const moments = await this.repo.listByUserIds([targetUid]);
    return this.paginate(moments, pagination);
  }

  /** Phan trang trong bo nho (list da sort postTime desc tu repository). */
  private paginate(moments: Moment[], { page, limit }: PaginationDto): PaginatedResult<Moment> {
    const start = (page - 1) * limit;
    return {
      items: moments.slice(start, start + limit),
      page,
      limit,
      total: moments.length,
    };
  }

  /** He thong tu danh dau da xem khi user luot qua moment tren feed. */
  async markSeen(momentId: string, viewerId: string): Promise<void> {
    const moment = await this.repo.findById(momentId);
    if (!moment) {
      throw new NotFoundException('Khong tim thay bai dang.');
    }
    await this.repo.markSeen(momentId, viewerId);
  }

  /**
   * Tha emoji (reaction bay). Day la "tuong tac qua lai" -> cap nhat friend streak
   * giua nguoi tha va chu bai dang.
   */
  async react(momentId: string, reactorId: string, emojiType: string): Promise<Reaction> {
    const moment = await this.repo.findById(momentId);
    if (!moment) {
      throw new NotFoundException('Khong tim thay bai dang.');
    }
    const reaction = await this.repo.addReaction(momentId, reactorId, emojiType);

    if (moment.userId !== reactorId) {
      await this.friendshipsService.registerInteraction(reactorId, moment.userId).catch((e) => {
        this.logger.warn(`Khong cap nhat duoc friend streak: ${e.message}`);
      });
    }
    return reaction;
  }

  async listReactions(momentId: string): Promise<Reaction[]> {
    return this.repo.listReactions(momentId);
  }

  /** Gom fcmTokens cua tat ca ban be roi gui push "X da dang khoanh khac moi". */
  private async notifyFriends(authUser: AuthUser): Promise<void> {
    const friendships = await this.friendshipsRepo.listAccepted(authUser.uid);
    const friendIds = friendships.map((f) => f.userIds.find((id) => id !== authUser.uid) ?? '');
    if (friendIds.length === 0) {
      return;
    }

    const friends = await Promise.all(friendIds.map((id) => this.usersRepo.findByUid(id)));
    const tokens = friends.flatMap((f) => f?.fcmTokens ?? []);
    if (tokens.length === 0) {
      return;
    }

    const me = await this.usersRepo.findByUid(authUser.uid);
    const senderName = me?.fullName ?? 'Ban be cua ban';

    const res = await this.firebase.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: 'Snapget',
        body: `${senderName} vua dang mot khoanh khac moi!`,
      },
    });
    this.logger.log(`FCM: gui ${res.successCount}/${tokens.length} thiet bi.`);
  }
}
