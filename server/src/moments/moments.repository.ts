import { Injectable } from '@nestjs/common';
import { Collections, SubCollections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { Moment, Reaction } from './entities/moment.entity';

/**
 * NOI DUY NHAT cham Firestore cho moments (bai dang) — collection 'posts'.
 * Giu nguyen tac 1-filter (khong can composite index); sort/paginate trong bo nho.
 */
@Injectable()
export class MomentsRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get col() {
    return this.firebase.firestore().collection(Collections.POSTS);
  }

  async create(moment: Omit<Moment, 'momentId'>): Promise<Moment> {
    // Firestore khong nhan gia tri undefined -> loc truoc khi ghi
    const data = Object.fromEntries(Object.entries(moment).filter(([, v]) => v !== undefined));
    const ref = await this.col.add(data);
    return { momentId: ref.id, ...moment };
  }

  async findById(momentId: string): Promise<Moment | null> {
    const snap = await this.col.doc(momentId).get();
    if (!snap.exists) {
      return null;
    }
    return this.toEntity(snap.id, snap.data() ?? {});
  }

  /**
   * Xoa moment + subcollection views/reactions (Firestore KHONG tu xoa
   * subcollection khi xoa doc cha). Batch 500 ops du cho quy mo DATN.
   */
  async delete(momentId: string): Promise<void> {
    const doc = this.col.doc(momentId);
    const [views, reactions] = await Promise.all([
      doc.collection(SubCollections.VIEWS).get(),
      doc.collection(SubCollections.REACTIONS).get(),
    ]);
    const batch = this.firebase.firestore().batch();
    views.docs.forEach((d) => batch.delete(d.ref));
    reactions.docs.forEach((d) => batch.delete(d.ref));
    batch.delete(doc);
    await batch.commit();
  }

  /**
   * Lay moment cua nhieu user (feed). whereIn gioi han 10 -> chunked(10).
   * Sort theo postTime desc lam trong bo nho (ne composite index).
   */
  async listByUserIds(userIds: string[]): Promise<Moment[]> {
    if (userIds.length === 0) {
      return [];
    }
    const chunks: string[][] = [];
    for (let i = 0; i < userIds.length; i += 10) {
      chunks.push(userIds.slice(i, i + 10));
    }
    const snaps = await Promise.all(
      chunks.map((chunk) => this.col.where('userId', 'in', chunk).get()),
    );
    const moments = snaps.flatMap((s) => s.docs.map((d) => this.toEntity(d.id, d.data())));
    return moments.sort((a, b) => b.postTime.localeCompare(a.postTime));
  }

  /**
   * Lay moment CHUP CHUNG ma cac user nay la NGUOI NHAN (coopUserId).
   * Can thiet vi moment co-op luu userId = nguoi moi — thieu query nay thi
   * moment chung khong bao gio hien voi nguoi nhan (feed/profile).
   */
  async listByCoopUserIds(userIds: string[]): Promise<Moment[]> {
    if (userIds.length === 0) {
      return [];
    }
    const chunks: string[][] = [];
    for (let i = 0; i < userIds.length; i += 10) {
      chunks.push(userIds.slice(i, i + 10));
    }
    const snaps = await Promise.all(
      chunks.map((chunk) => this.col.where('coopUserId', 'in', chunk).get()),
    );
    return snaps.flatMap((s) => s.docs.map((d) => this.toEntity(d.id, d.data())));
  }

  /** Danh dau da xem: posts/{id}/views/{viewerId}. */
  async markSeen(momentId: string, viewerId: string): Promise<void> {
    await this.col
      .doc(momentId)
      .collection(SubCollections.VIEWS)
      .doc(viewerId)
      .set({ viewerId, isSeen: true, seenAt: new Date().toISOString() });
  }

  /** Them reaction: posts/{id}/reactions (moi lan tha 1 doc — emoji bay nhieu lan duoc). */
  async addReaction(momentId: string, reactorId: string, emojiType: string): Promise<Reaction> {
    const reaction = {
      reactorId,
      emojiType,
      createdAt: new Date().toISOString(),
    };
    const ref = await this.col.doc(momentId).collection(SubCollections.REACTIONS).add(reaction);
    return { reactionId: ref.id, ...reaction };
  }

  async listReactions(momentId: string): Promise<Reaction[]> {
    const snap = await this.col.doc(momentId).collection(SubCollections.REACTIONS).get();
    return snap.docs.map((d) => ({
      reactionId: d.id,
      ...(d.data() as Omit<Reaction, 'reactionId'>),
    }));
  }

  private toEntity(momentId: string, data: FirebaseFirestore.DocumentData): Moment {
    return {
      momentId,
      userId: data.userId ?? '',
      contentType: data.contentType ?? 'PHOTO',
      mediaUrl: data.mediaUrl ?? data.thumbnailUrl ?? '',
      frameId: data.frameId,
      caption: data.caption,
      coopUserId: data.coopUserId,
      postTime: data.postTime ?? data.createdAt ?? '',
    };
  }
}
