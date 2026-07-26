import { Injectable, NotFoundException } from '@nestjs/common';
import { UsersRepository } from '../users/users.repository';
import { CreateFrameDto } from './dto/create-frame.dto';
import { UpdateFrameDto } from './dto/update-frame.dto';
import { Frame, FrameWithUnlock } from './entities/frame.entity';
import { FramesRepository } from './frames.repository';

@Injectable()
export class FramesService {
  constructor(
    private readonly repo: FramesRepository,
    private readonly usersRepo: UsersRepository,
  ) {}

  /** Catalog khung anh + trang thai da mo khoa cua user hien tai. */
  async listForUser(uid: string): Promise<FrameWithUnlock[]> {
    const [frames, user] = await Promise.all([this.repo.list(), this.usersRepo.findByUid(uid)]);
    const unlocked = new Set(user?.unlockedFrames ?? []);
    return frames.map((f) => ({ ...f, isUnlocked: unlocked.has(f.frameId) }));
  }

  /** Admin xem toan bo catalog khung (khong can trang thai unlock). */
  async listAll(): Promise<Frame[]> {
    return this.repo.list();
  }

  /** Admin them khung moi. */
  async create(dto: CreateFrameDto): Promise<Frame> {
    return this.repo.create({
      frameName: dto.frameName,
      imageUrl: dto.imageUrl,
      milestone: dto.milestone,
      createdAt: new Date().toISOString(),
    });
  }

  /** Admin sua khung (ten / anh / moc streak — milestone null = XOA moc). */
  async update(frameId: string, dto: UpdateFrameDto): Promise<Frame> {
    const frame = await this.repo.findById(frameId);
    if (!frame) {
      throw new NotFoundException('Khong tim thay khung anh.');
    }
    await this.repo.update(frameId, {
      frameName: dto.frameName,
      imageUrl: dto.imageUrl,
      // undefined = giu nguyen (repo loc undefined); null = ghi null de XOA moc
      milestone: dto.milestone,
    });
    return {
      ...frame,
      frameName: dto.frameName ?? frame.frameName,
      imageUrl: dto.imageUrl ?? frame.imageUrl,
      milestone: dto.milestone === undefined ? frame.milestone : dto.milestone,
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
}
