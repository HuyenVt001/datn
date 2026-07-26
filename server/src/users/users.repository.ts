import { Injectable } from '@nestjs/common';
import { FieldValue } from 'firebase-admin/firestore';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { PublicUser, User } from './entities/user.entity';

/**
 * NOI DUY NHAT cham Firestore cho domain users.
 * Map Firestore doc <-> entity o day; khong ro ri kieu Firestore ra ngoai.
 */
@Injectable()
export class UsersRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get col() {
    return this.firebase.firestore().collection(Collections.USERS);
  }

  async findByUid(uid: string): Promise<User | null> {
    const snap = await this.col.doc(uid).get();
    if (!snap.exists) {
      return null;
    }
    return this.toEntity(uid, snap.data() ?? {});
  }

  /** Tim user theo ma moi ket ban (phuc vu luong ket ban qua link). */
  async findByInviteCode(inviteCode: string): Promise<User | null> {
    const snap = await this.col.where('inviteCode', '==', inviteCode).limit(1).get();
    if (snap.empty) {
      return null;
    }
    const doc = snap.docs[0];
    return this.toEntity(doc.id, doc.data());
  }

  /** Tao doc user (dung khi dang nhap lan dau). Loc undefined — Firestore tu choi. */
  async create(user: User): Promise<void> {
    const { uid, ...data } = user;
    await this.col.doc(uid).set(this.stripUndefined(data));
  }

  async update(uid: string, patch: Partial<User>): Promise<void> {
    await this.col.doc(uid).set(this.stripUndefined(patch), { merge: true });
  }

  /** firebase-admin mac dinh THROW khi gap value undefined -> loc truoc khi ghi. */
  private stripUndefined<T extends object>(obj: T): T {
    return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined)) as T;
  }

  /** Them FCM token (khong trung lap). */
  async addFcmToken(uid: string, token: string): Promise<void> {
    await this.col.doc(uid).set({ fcmTokens: FieldValue.arrayUnion(token) }, { merge: true });
  }

  /** Go FCM token khi logout / het han. */
  async removeFcmToken(uid: string, token: string): Promise<void> {
    await this.col.doc(uid).update({ fcmTokens: FieldValue.arrayRemove(token) });
  }

  /** Mo khoa khung anh cho user (User_Frame — mang tren user doc, khong trung lap). */
  async unlockFrame(uid: string, frameId: string): Promise<void> {
    await this.col
      .doc(uid)
      .set({ unlockedFrames: FieldValue.arrayUnion(frameId) }, { merge: true });
  }

  /** Danh sach user dang so huu 1 khung (array-contains — dung 1 filter, khong can index). */
  async listByUnlockedFrame(frameId: string): Promise<User[]> {
    const snap = await this.col.where('unlockedFrames', 'array-contains', frameId).get();
    return snap.docs.map((d) => this.toEntity(d.id, d.data()));
  }

  private toEntity(uid: string, data: FirebaseFirestore.DocumentData): User {
    return {
      uid,
      email: data.email ?? '',
      fullName: data.fullName ?? data.name ?? '',
      avatar: data.avatar ?? data.avatarUrl,
      joinDate: data.joinDate ?? '',
      personalStreak: data.personalStreak ?? 0,
      lastStreakDate: data.lastStreakDate,
      birthday: data.birthday,
      inviteCode: data.inviteCode,
      inviteCodeExpiresAt: data.inviteCodeExpiresAt,
      unlockedFrames: data.unlockedFrames ?? [],
      fcmTokens: data.fcmTokens ?? [],
    };
  }

  toPublic(user: User): PublicUser {
    return {
      uid: user.uid,
      fullName: user.fullName,
      avatar: user.avatar,
      personalStreak: user.personalStreak,
    };
  }
}
