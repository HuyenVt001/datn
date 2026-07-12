import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { Friendship } from './entities/friendship.entity';

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

  /** Dem so ban ACCEPTED (phuc vu kiem tra gioi han 20). */
  async countAccepted(uid: string): Promise<number> {
    const snap = await this.col
      .where('userIds', 'array-contains', uid)
      .where('status', '==', 'ACCEPTED')
      .count()
      .get();
    return snap.data().count;
  }

  /** Danh sach ban ACCEPTED cua user. */
  async listAccepted(uid: string): Promise<Friendship[]> {
    const snap = await this.col
      .where('userIds', 'array-contains', uid)
      .where('status', '==', 'ACCEPTED')
      .get();
    return snap.docs.map((d) => d.data() as Friendship);
  }

  async create(a: string, b: string): Promise<Friendship> {
    const pairId = FriendshipsRepository.pairId(a, b);
    const [user1Id, user2Id] = [a, b].sort();
    const friendship: Friendship = {
      pairId,
      userIds: [user1Id, user2Id],
      user1Id,
      user2Id,
      friendStreak: 0,
      status: 'ACCEPTED',
      createdAt: new Date().toISOString(),
    };
    await this.col.doc(pairId).set(friendship);
    return friendship;
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
