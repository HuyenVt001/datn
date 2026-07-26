import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { Friendship } from './entities/friendship.entity';

/**
 * Ket qua tao LOI MOI ket ban trong transaction — repo KHONG nem HttpException,
 * service map outcome sang message nghiep vu tieng Viet.
 * MUTUAL_ACCEPTED: nguoi kia da moi minh truoc (PENDING nguoc chieu) -> 2 ben
 * cung muon ket ban -> chuyen thang ACCEPTED, khong can xac nhan them.
 */
export type RequestOutcome =
  | { outcome: 'REQUESTED'; friendship: Friendship }
  | { outcome: 'MUTUAL_ACCEPTED'; friendship: Friendship }
  | { outcome: 'ALREADY_FRIENDS' }
  | { outcome: 'ALREADY_REQUESTED' }
  | { outcome: 'LIMIT_REQUESTER' }
  | { outcome: 'LIMIT_INVITER' };

/** Ket qua chu link ACCEPT loi moi (trong transaction — chong race gioi han 20). */
export type AcceptOutcome =
  | { outcome: 'ACCEPTED'; friendship: Friendship }
  | { outcome: 'NOT_FOUND' }
  | { outcome: 'LIMIT_CURRENT' }
  | { outcome: 'LIMIT_REQUESTER' };

/** NOI DUY NHAT cham Firestore cho friendships. */
@Injectable()
export class FriendshipsRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get col() {
    return this.firebase.firestore().collection(Collections.FRIENDSHIPS);
  }

  /** Id cua cap ban = 2 uid sort roi noi — bao dam duy nhat & khong phu thuoc thu tu. */
  static pairId(a: string, b: string): string {
    return [a, b].sort().join('_');
  }

  async findPair(a: string, b: string): Promise<Friendship | null> {
    const snap = await this.col.doc(FriendshipsRepository.pairId(a, b)).get();
    return snap.exists ? (snap.data() as Friendship) : null;
  }

  /** Query dem so ban ACCEPTED cua 1 user (dung trong transaction connect). */
  private acceptedCountQuery(uid: string) {
    return this.col
      .where('userIds', 'array-contains', uid)
      .where('status', '==', 'ACCEPTED')
      .count();
  }

  /**
   * Tao LOI MOI ket ban (PENDING) trong MOT transaction: kiem tra cap + dem ban
   * 2 phia + tao doc atomic. Neu nguoi kia DA moi minh truoc (PENDING nguoc
   * chieu) -> 2 ben cung muon -> chuyen thang ACCEPTED (MUTUAL_ACCEPTED).
   */
  async createPendingRequest(
    requesterUid: string,
    inviterUid: string,
    maxFriends: number,
  ): Promise<RequestOutcome> {
    const pairId = FriendshipsRepository.pairId(requesterUid, inviterUid);
    const pairRef = this.col.doc(pairId);
    return this.firebase.firestore().runTransaction<RequestOutcome>(async (t) => {
      const pairSnap = await t.get(pairRef);
      const existing = pairSnap.exists ? (pairSnap.data() as Friendship) : null;
      if (existing?.status === 'ACCEPTED') {
        return { outcome: 'ALREADY_FRIENDS' };
      }
      if (existing?.status === 'PENDING' && existing.requesterUid === requesterUid) {
        return { outcome: 'ALREADY_REQUESTED' };
      }

      // Gioi han 20 kiem tra ngay tu luc gui loi moi (va kiem tra LAI khi accept)
      const [countRequester, countInviter] = await Promise.all([
        t.get(this.acceptedCountQuery(requesterUid)),
        t.get(this.acceptedCountQuery(inviterUid)),
      ]);
      if (countRequester.data().count >= maxFriends) {
        return { outcome: 'LIMIT_REQUESTER' };
      }
      if (countInviter.data().count >= maxFriends) {
        return { outcome: 'LIMIT_INVITER' };
      }

      // PENDING nguoc chieu (nguoi kia moi minh truoc) -> mutual, thanh ban luon
      if (existing?.status === 'PENDING') {
        const friendship: Friendship = { ...existing, status: 'ACCEPTED' };
        t.update(pairRef, { status: 'ACCEPTED' });
        return { outcome: 'MUTUAL_ACCEPTED', friendship };
      }

      const [user1Id, user2Id] = [requesterUid, inviterUid].sort();
      const friendship: Friendship = {
        pairId,
        userIds: [user1Id, user2Id],
        user1Id,
        user2Id,
        friendStreak: 0,
        status: 'PENDING',
        requesterUid,
        createdAt: new Date().toISOString(),
      };
      t.set(pairRef, friendship);
      return { outcome: 'REQUESTED', friendship };
    });
  }

  /**
   * Chu link CHAP NHAN loi moi — transaction kiem tra lai gioi han 20 ca 2 phia
   * (so ban co the da day tu luc loi moi duoc gui) roi doi status -> ACCEPTED.
   */
  async acceptRequest(
    accepterUid: string,
    requesterUid: string,
    maxFriends: number,
  ): Promise<AcceptOutcome> {
    const pairRef = this.col.doc(FriendshipsRepository.pairId(accepterUid, requesterUid));
    return this.firebase.firestore().runTransaction<AcceptOutcome>(async (t) => {
      const pairSnap = await t.get(pairRef);
      const existing = pairSnap.exists ? (pairSnap.data() as Friendship) : null;
      // Chi accept duoc loi moi PENDING ma NGUOI KIA gui (khong accept loi moi cua chinh minh)
      if (!existing || existing.status !== 'PENDING' || existing.requesterUid !== requesterUid) {
        return { outcome: 'NOT_FOUND' };
      }
      const [countAccepter, countRequester] = await Promise.all([
        t.get(this.acceptedCountQuery(accepterUid)),
        t.get(this.acceptedCountQuery(requesterUid)),
      ]);
      if (countAccepter.data().count >= maxFriends) {
        return { outcome: 'LIMIT_CURRENT' };
      }
      if (countRequester.data().count >= maxFriends) {
        return { outcome: 'LIMIT_REQUESTER' };
      }
      t.update(pairRef, { status: 'ACCEPTED' });
      return { outcome: 'ACCEPTED', friendship: { ...existing, status: 'ACCEPTED' } };
    });
  }

  /**
   * Chu link TU CHOI loi moi -> XOA doc (nguoi gui co the moi lai sau).
   * Tra ve false neu khong co loi moi PENDING tuong ung.
   * Chay trong TRANSACTION (fix 2026-07-26): decline tren thiet bi 2 khong duoc
   * phep xoa friendship vua duoc accept tren thiet bi 1 (check-then-delete cu
   * co khe ho giua get va delete).
   */
  async declineRequest(accepterUid: string, requesterUid: string): Promise<boolean> {
    const pairRef = this.col.doc(FriendshipsRepository.pairId(accepterUid, requesterUid));
    return this.firebase.firestore().runTransaction(async (txn) => {
      const snap = await txn.get(pairRef);
      const existing = snap.exists ? (snap.data() as Friendship) : null;
      if (!existing || existing.status !== 'PENDING' || existing.requesterUid !== requesterUid) {
        return false;
      }
      txn.delete(pairRef);
      return true;
    });
  }

  /** Danh sach loi moi PENDING dang cho uid (chu link) xac nhan, moi nhat truoc. */
  async listPendingRequests(uid: string): Promise<Friendship[]> {
    const snap = await this.col
      .where('userIds', 'array-contains', uid)
      .where('status', '==', 'PENDING')
      .get();
    return snap.docs
      .map((d) => d.data() as Friendship)
      .filter((f) => f.requesterUid && f.requesterUid !== uid)
      .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  }

  /** Danh sach ban ACCEPTED cua user. */
  async listAccepted(uid: string): Promise<Friendship[]> {
    const snap = await this.col
      .where('userIds', 'array-contains', uid)
      .where('status', '==', 'ACCEPTED')
      .get();
    return snap.docs.map((d) => d.data() as Friendship);
  }

  async delete(a: string, b: string): Promise<void> {
    await this.col.doc(FriendshipsRepository.pairId(a, b)).delete();
  }

  /** Cap nhat friend streak + moc tuong tac gan nhat. */
  async updateStreak(
    pairId: string,
    friendStreak: number,
    lastInteractionAt: string,
  ): Promise<void> {
    await this.col.doc(pairId).update({ friendStreak, lastInteractionAt });
  }
}
