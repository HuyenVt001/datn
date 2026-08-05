import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { AstriteTransaction, NewAstriteTransaction } from './entities/astrite-transaction.entity';

/**
 * NOI DUY NHAT cham Firestore cho "vi Astrite": so cai `astriteTransactions`
 * + cac field tien te tren user doc (`astrite`, `signupBonusClaimed`).
 *
 * Vi sao khong de trong UsersRepository: moi thay doi so du BAT BUOC chay cung
 * transaction voi dong so cai tuong ung, ma Firestore chi cho 1 callback
 * runTransaction -> gom vao 1 cho de khong bao gio lech so du <-> so cai.
 *
 * Cac ham `*InTransaction` nhan `tx` tu ngoai de gacha/topup gop nhieu thao tac
 * (tru tien + hoan tien + mo khoa vat pham + ghi lich su) vao MOT transaction.
 */
@Injectable()
export class AstriteRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get ledger() {
    return this.firebase.firestore().collection(Collections.ASTRITE_TRANSACTIONS);
  }

  private get users() {
    return this.firebase.firestore().collection(Collections.USERS);
  }

  /** Chay 1 khoi lam viec trong transaction Firestore. */
  runTransaction<T>(fn: (tx: FirebaseFirestore.Transaction) => Promise<T>): Promise<T> {
    return this.firebase.firestore().runTransaction(fn);
  }

  /** Doc snapshot user trong transaction (BAT BUOC doc truoc moi ghi). */
  async getUserInTransaction(
    tx: FirebaseFirestore.Transaction,
    uid: string,
  ): Promise<FirebaseFirestore.DocumentData | undefined> {
    const snap = await tx.get(this.users.doc(uid));
    return snap.exists ? snap.data() : undefined;
  }

  /** Doc so du hien tai trong transaction (doc thieu field -> 0). */
  async getBalanceInTransaction(tx: FirebaseFirestore.Transaction, uid: string): Promise<number> {
    const data = await this.getUserInTransaction(tx, uid);
    return (data?.astrite as number | undefined) ?? 0;
  }

  /** Ghi so du moi + cac field tien te kem theo (merge, khong dung den ho so). */
  setBalanceInTransaction(
    tx: FirebaseFirestore.Transaction,
    uid: string,
    balance: number,
    extra: Record<string, unknown> = {},
  ): void {
    tx.set(this.users.doc(uid), { astrite: balance, ...extra }, { merge: true });
  }

  /** Ghi 1 dong so cai trong transaction dang mo. */
  addEntryInTransaction(tx: FirebaseFirestore.Transaction, entry: NewAstriteTransaction): void {
    tx.set(this.ledger.doc(), { ...entry, createdAt: new Date().toISOString() });
  }

  async getBalance(uid: string): Promise<number> {
    const snap = await this.users.doc(uid).get();
    return (snap.data()?.astrite as number | undefined) ?? 0;
  }

  /**
   * Lich su giao dich cua 1 user, moi nhat truoc.
   * Dung 1 filter (`uid`) roi sort trong bo nho — TRANH phai tao composite index
   * (uid + createdAt). Quy mo DATN so dong/user nho nen chap nhan duoc; neu sau
   * nay du lieu lon thi tao index roi doi sang orderBy + startAfter.
   */
  async listByUid(uid: string, limit = 50): Promise<AstriteTransaction[]> {
    const snap = await this.ledger.where('uid', '==', uid).get();
    return snap.docs
      .map((d) => this.toEntity(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
  }

  private toEntity(id: string, data: FirebaseFirestore.DocumentData): AstriteTransaction {
    return {
      id,
      uid: data.uid ?? '',
      type: data.type,
      amount: data.amount ?? 0,
      balanceAfter: data.balanceAfter ?? 0,
      refId: data.refId,
      createdAt: data.createdAt ?? '',
    };
  }
}
