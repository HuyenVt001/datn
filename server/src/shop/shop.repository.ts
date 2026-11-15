import { Injectable } from '@nestjs/common';
import { FieldValue } from 'firebase-admin/firestore';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { ItemType, ShopItem } from './entities/shop-item.entity';
import { ShopPurchase } from './entities/shop-purchase.entity';

/** 3 mang so huu tren user doc. */
export type OwnershipField = 'unlockedFrames' | 'unlockedSkins' | 'unlockedEffects';

/** Trang thai vi + so huu cua user doc ra trong transaction mua hang. */
export interface UserWallet {
  exists: boolean;
  astrite: number;
  /** Tat ca chuan hoa ve CHUOI de so voi `refId`. */
  unlockedFrames: Set<string>;
  unlockedSkins: Set<string>;
  unlockedEffects: Set<string>;
}

/**
 * NOI DUY NHAT cham Firestore cho domain Shop: `shopItems`, `shopPurchases`, va
 * ghi mo khoa vat pham tren user doc TRONG transaction mua hang.
 *
 * Bat bien so du <-> so cai: ShopService ghi so du qua
 * `AstriteRepository.setBalanceInTransaction` + `addEntryInTransaction` trong
 * CUNG transaction voi `unlockInTransaction` + `addPurchaseInTransaction` o day.
 */
@Injectable()
export class ShopRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get items() {
    return this.firebase.firestore().collection(Collections.SHOP_ITEMS);
  }

  private get purchases() {
    return this.firebase.firestore().collection(Collections.SHOP_PURCHASES);
  }

  private get users() {
    return this.firebase.firestore().collection(Collections.USERS);
  }

  runTransaction<T>(fn: (tx: FirebaseFirestore.Transaction) => Promise<T>): Promise<T> {
    return this.firebase.firestore().runTransaction(fn);
  }

  // ==== Catalog ====

  /** Toan bo mat hang (admin). Sort trong bo nho de khong can composite index. */
  async listAllItems(): Promise<ShopItem[]> {
    const snap = await this.items.get();
    return snap.docs
      .map((d) => this.toItem(d.id, d.data()))
      .sort((a, b) => a.sortOrder - b.sortOrder || a.itemName.localeCompare(b.itemName));
  }

  /** Mat hang dang mo ban (catalog app). */
  async listActiveItems(): Promise<ShopItem[]> {
    return (await this.listAllItems()).filter((i) => i.isActive);
  }

  async findItemById(itemId: string): Promise<ShopItem | null> {
    const snap = await this.items.doc(itemId).get();
    return snap.exists ? this.toItem(snap.id, snap.data() ?? {}) : null;
  }

  /** Tim theo cap (itemType, refId) — 2 filter equality, Firestore tu xu ly khong can index. */
  async findItemByRef(itemType: ItemType, refId: string): Promise<ShopItem | null> {
    const snap = await this.items
      .where('itemType', '==', itemType)
      .where('refId', '==', refId)
      .limit(1)
      .get();
    return snap.empty ? null : this.toItem(snap.docs[0].id, snap.docs[0].data());
  }

  async createItem(item: Omit<ShopItem, 'itemId'>): Promise<ShopItem> {
    const ref = await this.items.add(stripUndefined(item));
    return { itemId: ref.id, ...item };
  }

  async updateItem(itemId: string, patch: Partial<Omit<ShopItem, 'itemId'>>): Promise<void> {
    const data = stripUndefined(patch);
    if (Object.keys(data).length === 0) {
      return; // Firestore update({}) nem loi — khong co gi de sua thi thoi
    }
    await this.items.doc(itemId).update(data);
  }

  async deleteItem(itemId: string): Promise<void> {
    await this.items.doc(itemId).delete();
  }

  // ==== Vi + so huu cua user ====

  async getWallet(uid: string): Promise<UserWallet> {
    const snap = await this.users.doc(uid).get();
    return this.toWallet(snap.exists, snap.data() ?? {});
  }

  /** Doc trong transaction (Firestore bat buoc doc het truoc khi ghi). */
  async getWalletInTransaction(
    tx: FirebaseFirestore.Transaction,
    uid: string,
  ): Promise<UserWallet> {
    const snap = await tx.get(this.users.doc(uid));
    return this.toWallet(snap.exists, snap.data() ?? {});
  }

  /** Mo khoa 1 vat pham trong transaction (arrayUnion — idempotent). */
  unlockInTransaction(
    tx: FirebaseFirestore.Transaction,
    uid: string,
    field: OwnershipField,
    value: string | number,
  ): void {
    tx.set(this.users.doc(uid), { [field]: FieldValue.arrayUnion(value) }, { merge: true });
  }

  // ==== Lich su mua ====

  /** Tao ref truoc de biet purchaseId dua vao so cai (refId) trong cung transaction. */
  newPurchaseRef(): FirebaseFirestore.DocumentReference {
    return this.purchases.doc();
  }

  addPurchaseInTransaction(
    tx: FirebaseFirestore.Transaction,
    ref: FirebaseFirestore.DocumentReference,
    purchase: Omit<ShopPurchase, 'purchaseId'>,
  ): void {
    tx.set(ref, purchase);
  }

  /** Lich su mua cua 1 user, moi nhat truoc (1 filter + sort bo nho, khong can index). */
  async listPurchasesByUid(uid: string, limit = 50): Promise<ShopPurchase[]> {
    const snap = await this.purchases.where('uid', '==', uid).get();
    return snap.docs
      .map((d) => this.toPurchase(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
  }

  /** Lich su toan he thong (admin) — loc chi tiet lam trong bo nho o service. */
  async listAllPurchases(limit = 1000): Promise<ShopPurchase[]> {
    const snap = await this.purchases.get();
    return snap.docs
      .map((d) => this.toPurchase(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
  }

  /** Dem luot mua tu 1 moc ISO (1 range filter tren `createdAt` — khong can index). */
  async countPurchasesSince(sinceIso: string): Promise<number> {
    const snap = await this.purchases.where('createdAt', '>=', sinceIso).count().get();
    return snap.data().count;
  }

  async countAllPurchases(): Promise<number> {
    const snap = await this.purchases.count().get();
    return snap.data().count;
  }

  // ==== Mapping ====

  private toWallet(exists: boolean, data: FirebaseFirestore.DocumentData): UserWallet {
    const toSet = (v: unknown) => new Set<string>(((v as unknown[]) ?? []).map(String));
    return {
      exists,
      astrite: (data.astrite as number | undefined) ?? 0,
      unlockedFrames: toSet(data.unlockedFrames),
      unlockedSkins: toSet(data.unlockedSkins),
      unlockedEffects: toSet(data.unlockedEffects),
    };
  }

  private toItem(itemId: string, data: FirebaseFirestore.DocumentData): ShopItem {
    return {
      itemId,
      itemName: data.itemName ?? '',
      itemType: data.itemType as ItemType,
      refId: String(data.refId ?? ''),
      price: typeof data.price === 'number' ? data.price : 0,
      imageUrl: data.imageUrl,
      isActive: data.isActive ?? true,
      sortOrder: data.sortOrder ?? 0,
      createdAt: data.createdAt ?? '',
    };
  }

  private toPurchase(purchaseId: string, data: FirebaseFirestore.DocumentData): ShopPurchase {
    return {
      purchaseId,
      uid: data.uid ?? '',
      itemId: data.itemId ?? '',
      itemName: data.itemName ?? '',
      itemType: data.itemType as ItemType,
      refId: String(data.refId ?? ''),
      price: data.price ?? 0,
      balanceAfter: data.balanceAfter ?? 0,
      createdAt: data.createdAt ?? '',
    };
  }
}

/** Firestore khong nhan `undefined` — bo cac field undefined truoc khi ghi. */
function stripUndefined<T extends object>(obj: T): Record<string, unknown> {
  return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined));
}
