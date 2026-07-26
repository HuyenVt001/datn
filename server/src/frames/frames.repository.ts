import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { Frame } from './entities/frame.entity';

/** NOI DUY NHAT cham Firestore cho frames (khung anh phan thuong). */
@Injectable()
export class FramesRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get col() {
    return this.firebase.firestore().collection(Collections.FRAMES);
  }

  async list(): Promise<Frame[]> {
    const snap = await this.col.get();
    return snap.docs.map((d) => this.toEntity(d.id, d.data()));
  }

  async findById(frameId: string): Promise<Frame | null> {
    const snap = await this.col.doc(frameId).get();
    if (!snap.exists) {
      return null;
    }
    return this.toEntity(snap.id, snap.data() ?? {});
  }

  async create(frame: Omit<Frame, 'frameId'>): Promise<Frame> {
    const ref = await this.col.add(
      Object.fromEntries(Object.entries(frame).filter(([, v]) => v !== undefined)),
    );
    return { frameId: ref.id, ...frame };
  }

  async update(frameId: string, patch: Partial<Omit<Frame, 'frameId'>>): Promise<void> {
    const data = Object.fromEntries(Object.entries(patch).filter(([, v]) => v !== undefined));
    if (Object.keys(data).length === 0) {
      return; // Firestore update({}) se nem loi — khong co gi de sua thi thoi
    }
    await this.col.doc(frameId).update(data);
  }

  async delete(frameId: string): Promise<void> {
    await this.col.doc(frameId).delete();
  }

  /**
   * Map doc -> entity. Doc CU (truoc 2026-07-26) chi co `milestone`:
   * co milestone -> STREAK_MILESTONE, khong -> QUEST_RANDOM (dung logic thuong cu).
   * `milestone` xuat ra luon dong bo voi unlockType de app cu doc khong sai.
   */
  private toEntity(frameId: string, data: FirebaseFirestore.DocumentData): Frame {
    const legacyMilestone = typeof data.milestone === 'number' ? data.milestone : undefined;
    const unlockType =
      (data.unlockType as Frame['unlockType']) ??
      (legacyMilestone ? 'STREAK_MILESTONE' : 'QUEST_RANDOM');
    const unlockValue =
      typeof data.unlockValue === 'number' ? data.unlockValue : (legacyMilestone ?? null);
    return {
      frameId,
      frameName: data.frameName ?? '',
      imageUrl: data.imageUrl,
      unlockType,
      unlockValue,
      milestone: unlockType === 'STREAK_MILESTONE' ? unlockValue : null,
      createdAt: data.createdAt ?? '',
    };
  }
}
