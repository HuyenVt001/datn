import { ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { PaginatedResult, PaginationDto } from '../common/dto/pagination.dto';
import { AuthUser } from '../common/decorators/current-user.decorator';
import { FramesService } from '../frames/frames.service';
import { FriendshipsRepository } from '../friendships/friendships.repository';
import { FriendshipsService } from '../friendships/friendships.service';
import { QuestsService } from '../quests/quests.service';
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
    private readonly questsService: QuestsService,
    private readonly framesService: FramesService,
  ) {}

  /**
   * Dang moment: tao doc -> tang personal streak -> bao ban be qua FCM.
   * mediaUrl da qua POST /upload (Cloudinary da enforce video <= 5s).
   */
  async create(authUser: AuthUser, dto: CreateMomentDto, coopUserId?: string): Promise<Moment> {
    const moment = await this.repo.create({
      userId: authUser.uid,
      contentType: dto.contentType,
      mediaUrl: dto.mediaUrl,
      frameId: dto.frameId,
      caption: dto.caption,
      coopUserId, // co-op capture: moment chung cua 2 nguoi
      postTime: new Date().toISOString(),
    });

    // Business rule: mo app + upload >= 1 moment/ngay -> tang personal streak.
    let personalStreak = 0;
    try {
      personalStreak = await this.usersService.registerActivityForStreak(authUser.uid);
    } catch (e) {
      this.logger.warn(`Khong cap nhat duoc personal streak: ${(e as Error).message}`);
    }

    // Quest: dang bai = hoan thanh POST_MOMENT; dong thoi xet thuong khung theo moc streak.
    await this.questsService.registerMomentPosted(authUser.uid, personalStreak).catch((e) => {
      this.logger.warn(`Khong cap nhat duoc quest: ${e.message}`);
    });

    // Khung dieu kien POST_COUNT: dem tong bai roi mo cac khung vua dat nguong (best-effort).
    await this.unlockPostCountFrames(authUser.uid).catch((e) => {
      this.logger.warn(`Khong mo duoc khung theo so bai dang: ${(e as Error).message}`);
    });

    // Gui push cho danh sach ban be (khong chan luong dang bai neu loi).
    await this.notifyFriends(authUser).catch((e) => {
      this.logger.warn(`Khong gui duoc FCM: ${e.message}`);
    });

    return moment;
  }

  /**
   * Feed = moment cua minh + ban be, sort postTime desc, paginate trong bo nho.
   * Gom ca moment CHUP CHUNG ma minh/ban be la NGUOI NHAN (coopUserId) —
   * moment co-op luu userId = nguoi moi nen thieu buoc nay la "moment cua ca 2"
   * chi hien voi 1 nguoi.
   */
  async getFeed(uid: string, pagination: PaginationDto): Promise<PaginatedResult<Moment>> {
    const friendships = await this.friendshipsRepo.listAccepted(uid);
    const friendIds = friendships.map((f) => f.userIds.find((id) => id !== uid) ?? '');
    const ids = [uid, ...friendIds];
    const [own, coop] = await Promise.all([
      this.repo.listByUserIds(ids),
      this.repo.listByCoopUserIds(ids),
    ]);
    return this.paginate(this.mergeMoments(own, coop), pagination);
  }

  /** Moment cua chinh minh (profile: calendar + dem tong so bai) — gom ca moment chup chung. */
  async listMine(uid: string, pagination: PaginationDto): Promise<PaginatedResult<Moment>> {
    const [own, coop] = await Promise.all([
      this.repo.listByUserIds([uid]),
      this.repo.listByCoopUserIds([uid]),
    ]);
    return this.paginate(this.mergeMoments(own, coop), pagination);
  }

  /** Moment cua 1 user khac — CHI ban be (hoac chinh minh) xem duoc; gom ca moment chup chung. */
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
    const [own, coop] = await Promise.all([
      this.repo.listByUserIds([targetUid]),
      this.repo.listByCoopUserIds([targetUid]),
    ]);
    return this.paginate(this.mergeMoments(own, coop), pagination);
  }

  /** Khung dieu kien POST_COUNT: dem tong bai cua user roi mo cac khung dat nguong. */
  private async unlockPostCountFrames(uid: string): Promise<void> {
    const count = await this.repo.countByUserId(uid);
    await this.framesService.unlockByThreshold(uid, 'POST_COUNT', count);
  }

  /** Gop 2 danh sach moment, khu trung theo momentId, sort postTime desc. */
  private mergeMoments(own: Moment[], coop: Moment[]): Moment[] {
    const seen = new Set(own.map((m) => m.momentId));
    const merged = [...own, ...coop.filter((m) => !seen.has(m.momentId))];
    return merged.sort((a, b) => b.postTime.localeCompare(a.postTime));
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

  /** Xoa moment — CHI chu bai xoa duoc (nguoi duoc tag co-op cung khong). */
  async delete(momentId: string, uid: string): Promise<void> {
    const moment = await this.repo.findById(momentId);
    if (!moment) {
      throw new NotFoundException('Khong tim thay bai dang.');
    }
    if (moment.userId !== uid) {
      throw new ForbiddenException('Chi chu bai moi xoa duoc bai dang nay.');
    }
    await this.repo.delete(momentId);
  }

  /** He thong tu danh dau da xem khi user luot qua moment tren feed. */
  async markSeen(momentId: string, viewerId: string): Promise<void> {
    const moment = await this.getMomentForInteraction(momentId, viewerId);
    await this.repo.markSeen(moment.momentId, viewerId);
  }

  /**
   * Tha emoji (reaction bay). Day la "tuong tac qua lai" -> cap nhat friend streak
   * giua nguoi tha va chu bai dang.
   */
  async react(momentId: string, reactorId: string, emojiType: string): Promise<Reaction> {
    const moment = await this.getMomentForInteraction(momentId, reactorId);
    const reaction = await this.repo.addReaction(momentId, reactorId, emojiType);

    if (moment.userId !== reactorId) {
      await this.friendshipsService.registerInteraction(reactorId, moment.userId).catch((e) => {
        this.logger.warn(`Khong cap nhat duoc friend streak: ${e.message}`);
      });
    }
    return reaction;
  }

  async listReactions(momentId: string, viewerId: string): Promise<Reaction[]> {
    await this.getMomentForInteraction(momentId, viewerId);
    return this.repo.listReactions(momentId);
  }

  /**
   * Lay moment de TUONG TAC (seen/reaction) — chi nguoi thay duoc moment tren
   * feed moi duoc tuong tac (fix 2026-07-26: truoc day ai cam momentId cung
   * seen/react duoc, ke ca nguoi la): chinh chu bai / nguoi chup chung /
   * ban be cua MOT TRONG HAI nguoi tren anh.
   */
  private async getMomentForInteraction(momentId: string, uid: string): Promise<Moment> {
    const moment = await this.repo.findById(momentId);
    if (!moment) {
      throw new NotFoundException('Khong tim thay bai dang.');
    }
    if (moment.userId === uid || moment.coopUserId === uid) {
      return moment;
    }
    const authorPair = await this.friendshipsRepo.findPair(uid, moment.userId);
    if (authorPair?.status === 'ACCEPTED') {
      return moment;
    }
    if (moment.coopUserId) {
      const coopPair = await this.friendshipsRepo.findPair(uid, moment.coopUserId);
      if (coopPair?.status === 'ACCEPTED') {
        return moment;
      }
    }
    throw new ForbiddenException('Chi tuong tac duoc voi bai dang cua ban be.');
  }

  /** Bao ban be "X da dang khoanh khac moi" (helper push chung o UsersService). */
  private async notifyFriends(authUser: AuthUser): Promise<void> {
    const friendships = await this.friendshipsRepo.listAccepted(authUser.uid);
    const friendIds = friendships.map((f) => f.userIds.find((id) => id !== authUser.uid) ?? '');
    if (friendIds.length === 0) {
      return;
    }
    const me = await this.usersRepo.findByUid(authUser.uid);
    const senderName = me?.fullName ?? 'Ban be cua ban';
    await this.usersService.pushToUids(
      friendIds,
      'Snapget',
      `${senderName} vua dang mot khoanh khac moi!`,
    );
  }
}
