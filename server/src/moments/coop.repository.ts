import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { CoopInvite, CoopInviteStatus } from './entities/coop-invite.entity';

/** NOI DUY NHAT cham Firestore cho loi moi chup chung — collection 'coopInvites'. */
@Injectable()
export class CoopRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get col() {
    return this.firebase.firestore().collection(Collections.COOP_INVITES);
  }

  async create(invite: Omit<CoopInvite, 'inviteId'>): Promise<CoopInvite> {
    const data = Object.fromEntries(Object.entries(invite).filter(([, v]) => v !== undefined));
    const ref = await this.col.add(data);
    return { inviteId: ref.id, ...invite };
  }

  async findById(inviteId: string): Promise<CoopInvite | null> {
    const snap = await this.col.doc(inviteId).get();
    if (!snap.exists) {
      return null;
    }
    return this.toEntity(snap.id, snap.data() ?? {});
  }

  /** Loi moi dang cho cua 1 nguoi nhan (1-filter equality; loc status trong bo nho). */
  async listPendingForInvitee(inviteeId: string): Promise<CoopInvite[]> {
    const snap = await this.col.where('inviteeId', '==', inviteeId).get();
    return snap.docs
      .map((d) => this.toEntity(d.id, d.data()))
      .filter((i) => i.status === 'PENDING')
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  async update(inviteId: string, patch: Partial<Omit<CoopInvite, 'inviteId'>>): Promise<void> {
    const data = Object.fromEntries(Object.entries(patch).filter(([, v]) => v !== undefined));
    await this.col.doc(inviteId).set(data, { merge: true });
  }

  /**
   * Chuyen PENDING -> COMPLETED bang TRANSACTION (optimistic lock).
   * Tra ve false neu loi moi khong con PENDING — chong 2 request accept
   * dong thoi cung tao moment (accept mat vai giay de ghep anh).
   */
  async markCompletedIfPending(inviteId: string): Promise<boolean> {
    return this.transitionIfPending(inviteId, 'COMPLETED');
  }

  /**
   * Chuyen PENDING -> DECLINED bang TRANSACTION — decline khong duoc ghi de
   * len COMPLETED (accept dang ghep anh) hay nguoc lai.
   */
  async markDeclinedIfPending(inviteId: string): Promise<boolean> {
    return this.transitionIfPending(inviteId, 'DECLINED');
  }

  private async transitionIfPending(inviteId: string, to: CoopInviteStatus): Promise<boolean> {
    const ref = this.col.doc(inviteId);
    return this.firebase.firestore().runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists || (snap.data()?.status ?? '') !== 'PENDING') {
        return false;
      }
      tx.update(ref, { status: to, respondedAt: new Date().toISOString() });
      return true;
    });
  }

  private toEntity(inviteId: string, data: FirebaseFirestore.DocumentData): CoopInvite {
    return {
      inviteId,
      inviterId: data.inviterId ?? '',
      inviteeId: data.inviteeId ?? '',
      inviterMediaUrl: data.inviterMediaUrl ?? '',
      status: data.status ?? 'PENDING',
      createdAt: data.createdAt ?? '',
      respondedAt: data.respondedAt,
      momentId: data.momentId,
    };
  }
}
