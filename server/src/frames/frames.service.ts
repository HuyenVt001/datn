import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { MAX_FRIENDS, STREAK_MILESTONES } from '../common/constants';
import { UsersRepository } from '../users/users.repository';
import { CreateFrameDto } from './dto/create-frame.dto';
import { UpdateFrameDto } from './dto/update-frame.dto';
import { Frame, FrameOwner, FrameWithUnlock, UnlockType } from './entities/frame.entity';
import { FramesRepository } from './frames.repository';

@Injectable()
export class FramesService {
  constructor(
    private readonly repo: FramesRepository,
    private readonly usersRepo: UsersRepository,
  ) {}

  /** Catalog khung anh + trang thai da mo khoa cua user hien tai (DEFAULT = mo san). */
  async listForUser(uid: string): Promise<FrameWithUnlock[]> {
    const [frames, user] = await Promise.all([this.repo.list(), this.usersRepo.findByUid(uid)]);
    const unlocked = new Set(user?.unlockedFrames ?? []);
    return frames.map((f) => ({
      ...f,
      isUnlocked: f.unlockType === 'DEFAULT' || unlocked.has(f.frameId),
    }));
  }

  /** Admin xem toan bo catalog khung (khong can trang thai unlock). */
  async listAll(): Promise<Frame[]> {
    return this.repo.list();
  }

  /** Admin xem danh sach user dang so huu 1 khung (khong ap dung cho DEFAULT — moi user deu co). */
  async listOwners(frameId: string): Promise<{ frame: Frame; owners: FrameOwner[] }> {
    const frame = await this.repo.findById(frameId);
    if (!frame) {
      throw new NotFoundException('Khong tim thay khung anh.');
    }
    const users = await this.usersRepo.listByUnlockedFrame(frameId);
    const owners = users.map((u) => ({
      uid: u.uid,
      email: u.email || undefined,
      fullName: u.fullName,
      avatar: u.avatar,
    }));
    return { frame, owners };
  }

  /** Admin them khung moi. */
  async create(dto: CreateFrameDto): Promise<Frame> {
    const unlockType = dto.unlockType ?? 'GACHA';
    const unlockValue = this.assertUnlockRule(unlockType, dto.unlockValue);
    return this.repo.create({
      frameName: dto.frameName,
      imageUrl: dto.imageUrl,
      unlockType,
      unlockValue,
      // giu field legacy dong bo cho app cu (nhan "moc streak")
      milestone: unlockType === 'STREAK_MILESTONE' ? unlockValue : null,
      createdAt: new Date().toISOString(),
    });
  }

  /**
   * Admin sua khung (ten / anh / dieu kien mo khoa).
   * Doi unlockType -> nguong lay tu dto (KHONG mang nguong loai cu sang loai moi);
   * giu nguyen loai -> khong gui unlockValue thi giu nguong cu.
   */
  async update(frameId: string, dto: UpdateFrameDto): Promise<Frame> {
    const frame = await this.repo.findById(frameId);
    if (!frame) {
      throw new NotFoundException('Khong tim thay khung anh.');
    }

    const unlockType = dto.unlockType ?? frame.unlockType;
    const valueInput =
      dto.unlockValue !== undefined
        ? dto.unlockValue
        : unlockType === frame.unlockType
          ? (frame.unlockValue ?? undefined)
          : undefined;
    const unlockValue = this.assertUnlockRule(unlockType, valueInput);

    const patch = {
      frameName: dto.frameName,
      imageUrl: dto.imageUrl,
      unlockType,
      unlockValue,
      milestone: unlockType === 'STREAK_MILESTONE' ? unlockValue : null,
    };
    await this.repo.update(frameId, patch);
    return {
      ...frame,
      frameName: dto.frameName ?? frame.frameName,
      imageUrl: dto.imageUrl ?? frame.imageUrl,
      unlockType,
      unlockValue,
      milestone: patch.milestone,
    };
  }

  /** Admin xoa khung. */
  async delete(frameId: string): Promise<void> {
    const frame = await this.repo.findById(frameId);
    if (!frame) {
      throw new NotFoundException('Khong tim thay khung anh.');
    }
    await this.repo.delete(frameId);
  }

  /**
   * Mo khoa khung cho user — dung lam PHAN THUONG (streak/quest goi ham nay,
   * admin cung co the grant thu cong de demo).
   */
  async unlockForUser(uid: string, frameId: string): Promise<void> {
    const frame = await this.repo.findById(frameId);
    if (!frame) {
      throw new NotFoundException('Khong tim thay khung anh.');
    }
    await this.usersRepo.unlockFrame(uid, frameId);
  }

  /**
   * Tu dong mo cac khung dieu kien NGUONG (POST_COUNT / FRIEND_COUNT) ma user
   * vua dat duoc. Goi tu MomentsService (sau khi dang bai) va FriendshipsService
   * (sau khi ket ban thanh cong). Idempotent — khung da so huu thi bo qua.
   */
  async unlockByThreshold(
    uid: string,
    type: Extract<UnlockType, 'POST_COUNT' | 'FRIEND_COUNT'>,
    count: number,
  ): Promise<string[]> {
    return this.unlockMatching(uid, (f) => f.unlockType === type && (f.unlockValue ?? 0) <= count);
  }

  /** Tu dong mo cac khung COOP_FIRST — goi cho CA 2 nguoi khi hoan tat chup chung. */
  async unlockCoopFrames(uid: string): Promise<string[]> {
    return this.unlockMatching(uid, (f) => f.unlockType === 'COOP_FIRST');
  }

  /** Mo moi khung thoa dieu kien ma user CHUA so huu; tra ve danh sach frameId vua mo. */
  private async unlockMatching(
    uid: string,
    predicate: (frame: Frame) => boolean,
  ): Promise<string[]> {
    const [frames, user] = await Promise.all([this.repo.list(), this.usersRepo.findByUid(uid)]);
    const owned = new Set(user?.unlockedFrames ?? []);
    const eligible = frames.filter((f) => predicate(f) && !owned.has(f.frameId));
    await Promise.all(eligible.map((f) => this.usersRepo.unlockFrame(uid, f.frameId)));
    return eligible.map((f) => f.frameId);
  }

  /** Kiem tra nguong N hop le theo tung loai dieu kien; tra ve nguong da chuan hoa (null neu khong can). */
  private assertUnlockRule(type: UnlockType, value: number | undefined): number | null {
    switch (type) {
      case 'STREAK_MILESTONE':
        if (!value || !STREAK_MILESTONES.includes(value)) {
          throw new BadRequestException(
            `Moc streak phai la mot trong: ${STREAK_MILESTONES.join(', ')}.`,
          );
        }
        return value;
      case 'POST_COUNT':
        if (!value || value < 1) {
          throw new BadRequestException('Dieu kien so bai dang can nguong N >= 1.');
        }
        return value;
      case 'FRIEND_COUNT':
        if (!value || value < 1 || value > MAX_FRIENDS) {
          throw new BadRequestException(
            `Dieu kien so ban be can nguong N tu 1 den ${MAX_FRIENDS}.`,
          );
        }
        return value;
      default:
        // GACHA / COOP_FIRST / DEFAULT khong co nguong
        return null;
    }
  }
}
