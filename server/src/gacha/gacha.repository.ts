import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { GachaPity } from '../users/entities/user.entity';
import { GachaItem, ItemRarity, ItemType } from './entities/gacha-item.entity';
import { GachaRoll, RollResultEntry, RollType } from './entities/gacha-roll.entity';

/** Trang thai gacha cua 1 user doc ra luc bat dau transaction quay. */
export interface UserGachaState {
  astrite: number;
  pity: GachaPity;
  unlockedFrames: Set<string>;
  unlockedSkins: Set<string>;
  unlockedEffects: Set<string>;
}

/**
 * NOI DUY NHAT cham Firestore cho domain gacha: `gachaItems`, `gachaRolls`,
 * va cac field gacha tren user doc (`gachaPity`, `unlockedSkins/Effects`).
 *
 * ⚠️ Ghi chu kien truc: ham `applyRollInTransaction` ghi LUON ca `astrite`
 * (vi cua AstriteRepository) trong cung 1 `tx.set` — vi 1 lan quay vua tru
 * tien vua hoan tien vua mo khoa vat pham, phai nguyen tu. Bat bien "moi thay
 * doi so du deu co dong so cai" van duoc giu: GachaService goi
 * `AstriteRepository.addEntryInTransaction` trong CUNG transaction do.
 */
@Injectable()
export class GachaRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get items() {
    return this.firebase.firestore().collection(Collections.GACHA_ITEMS);
  }

  private get rolls() {
    return this.firebase.firestore().collection(Collections.GACHA_ROLLS);
  }

  private get users() {
    return this.firebase.firestore().collection(Collections.USERS);
  }

  runTransaction<T>(fn: (tx: FirebaseFirestore.Transaction) => Promise<T>): Promise<T> {
    return this.firebase.firestore().runTransaction(fn);
  }

  // ==== Catalog vat pham ====

  /** Toan bo catalog (admin). Sort trong bo nho de khong can composite index. */
  async listAllItems(): Promise<GachaItem[]> {
    const snap = await this.items.get();
    return snap.docs
      .map((d) => this.toItem(d.id, d.data()))
      .sort((a, b) => a.sortOrder - b.sortOrder || a.itemName.localeCompare(b.itemName));
  }

  /** Vat pham dang bat — day la POOL quay that. */
  async listActiveItems(): Promise<GachaItem[]> {
    return (await this.listAllItems()).filter((i) => i.isActive);
  }

  async findItemByRef(itemType: ItemType, refId: string): Promise<GachaItem | null> {
    const snap = await this.items
      .where('itemType', '==', itemType)
      .where('refId', '==', refId)
      .limit(1)
      .get();
    return snap.empty ? null : this.toItem(snap.docs[0].id, snap.docs[0].data());
  }

  async findItemById(itemId: string): Promise<GachaItem | null> {
    const snap = await this.items.doc(itemId).get();
    return snap.exists ? this.toItem(snap.id, snap.data() ?? {}) : null;
  }

  async createItem(item: Omit<GachaItem, 'itemId'>): Promise<GachaItem> {
    const ref = await this.items.add(
      Object.fromEntries(Object.entries(item).filter(([, v]) => v !== undefined)),
    );
    return { itemId: ref.id, ...item };
  }

  async updateItem(itemId: string, patch: Partial<Omit<GachaItem, 'itemId'>>): Promise<void> {
    const data = Object.fromEntries(Object.entries(patch).filter(([, v]) => v !== undefined));
    if (Object.keys(data).length === 0) {
      return; // Firestore update({}) nem loi — khong co gi de sua thi thoi
    }
    await this.items.doc(itemId).update(data);
  }

  async deleteItem(itemId: string): Promise<void> {
    await this.items.doc(itemId).delete();
  }

  // ==== Trang thai gacha cua user ====

  /** Doc trang thai truoc khi ghi (Firestore bat buoc doc het roi moi ghi). */
  async getUserStateInTransaction(
    tx: FirebaseFirestore.Transaction,
    uid: string,
  ): Promise<UserGachaState> {
    const snap = await tx.get(this.users.doc(uid));
    return this.toState(snap.data() ?? {});
  }

  /** Doc trang thai ngoai transaction (cho cac endpoint chi doc). */
  async getUserState(uid: string): Promise<UserGachaState> {
    const snap = await this.users.doc(uid).get();
    return this.toState(snap.data() ?? {});
  }

  private toState(data: FirebaseFirestore.DocumentData): UserGachaState {
    return {
      astrite: (data.astrite as number | undefined) ?? 0,
      pity: {
        R: data.gachaPity?.R ?? 0,
        SR: data.gachaPity?.SR ?? 0,
        SSR: data.gachaPity?.SSR ?? 0,
      },
      unlockedFrames: new Set<string>(((data.unlockedFrames as string[]) ?? []).map(String)),
      unlockedSkins: new Set<string>(((data.unlockedSkins as unknown[]) ?? []).map(String)),
      unlockedEffects: new Set<string>(((data.unlockedEffects as unknown[]) ?? []).map(String)),
    };
  }

  /**
   * Ghi ket qua 1 lan quay len user doc: so du moi, pity moi, cac vat pham vua
   * mo khoa. `unlockedSkins`/`unlockedEffects` luu dang SO (khop id int trong
   * app), `unlockedFrames` giu dang chuoi nhu cu.
   */
  applyRollInTransaction(
    tx: FirebaseFirestore.Transaction,
    uid: string,
    state: UserGachaState,
  ): void {
    tx.set(
      this.users.doc(uid),
      {
        astrite: state.astrite,
        gachaPity: state.pity,
        unlockedFrames: [...state.unlockedFrames],
        unlockedSkins: [...state.unlockedSkins].map(Number),
        unlockedEffects: [...state.unlockedEffects].map(Number),
      },
      { merge: true },
    );
  }

  // ==== Lich su quay ====

  /** Tao ref truoc de biet rollId dua vao so cai (refId) trong cung transaction. */
  newRollRef(): FirebaseFirestore.DocumentReference {
    return this.rolls.doc();
  }

  addRollInTransaction(
    tx: FirebaseFirestore.Transaction,
    ref: FirebaseFirestore.DocumentReference,
    roll: Omit<GachaRoll, 'rollId' | 'createdAt'>,
  ): void {
    tx.set(ref, { ...roll, createdAt: new Date().toISOString() });
  }

  /** Lich su quay cua 1 user, moi nhat truoc (1 filter + sort bo nho, khong can index). */
  async listRollsByUid(uid: string, limit = 50): Promise<GachaRoll[]> {
    const snap = await this.rolls.where('uid', '==', uid).get();
    return snap.docs
      .map((d) => this.toRoll(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
  }

  /**
   * Lich su toan he thong (admin). Loc uid/bac/ngay lam TRONG BO NHO o service
   * — quy mo DATN, va tranh phai tao composite index cho tung to hop loc.
   */
  async listAllRolls(limit = 200): Promise<GachaRoll[]> {
    const snap = await this.rolls.get();
    return snap.docs
      .map((d) => this.toRoll(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
  }

  /**
   * Dem so LUOT BAM NUT quay ke tu mot moc ISO (dashboard admin).
   * 1 range filter tren 1 field `createdAt` — khong can composite index.
   */
  async countRollsSince(sinceIso: string): Promise<number> {
    const snap = await this.rolls.where('createdAt', '>=', sinceIso).count().get();
    return snap.data().count;
  }

  /** Tong so luot bam nut quay tu truoc toi nay. */
  async countAllRolls(): Promise<number> {
    const snap = await this.rolls.count().get();
    return snap.data().count;
  }

  private toItem(itemId: string, data: FirebaseFirestore.DocumentData): GachaItem {
    return {
      itemId,
      itemName: data.itemName ?? '',
      itemType: data.itemType as ItemType,
      rarity: data.rarity as ItemRarity,
      imageUrl: data.imageUrl,
      refId: String(data.refId ?? ''),
      isActive: data.isActive ?? true,
      sortOrder: data.sortOrder ?? 0,
      createdAt: data.createdAt ?? '',
    };
  }

  private toRoll(rollId: string, data: FirebaseFirestore.DocumentData): GachaRoll {
    return {
      rollId,
      uid: data.uid ?? '',
      rollType: data.rollType as RollType,
      cost: data.cost ?? 0,
      results: (data.results ?? []) as RollResultEntry[],
      refundTotal: data.refundTotal ?? 0,
      balanceAfter: data.balanceAfter ?? 0,
      createdAt: data.createdAt ?? '',
    };
  }
}
