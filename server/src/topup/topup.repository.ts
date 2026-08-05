import { Injectable } from '@nestjs/common';
import { Collections } from '../common/constants';
import { FirebaseService } from '../firebase/firebase.service';
import { TopupOrder, TopupOrderStatus } from './entities/topup-order.entity';
import { TopupPackage } from './entities/topup-package.entity';

/**
 * NOI DUY NHAT cham Firestore cho domain nap tien: `topupPackages` +
 * `topupOrders`.
 *
 * ⚠️ Hai diem thiet ke quan trong:
 *  1. **Doc id cua don = `orderCode`** — webhook goi lai bao nhieu lan cung tro
 *     ve dung 1 doc, khong the sinh ra don thu hai cho cung ma.
 *  2. `createOrder` dung `create()` (KHONG phai `set()`): Firestore nem
 *     ALREADY_EXISTS neu ma da ton tai, nho vay viec sinh `orderCode` trung
 *     duoc phat hien ngay thay vi ghi de len don cu.
 *
 * Viec cong Astrite khong nam o day: no phai chay CUNG transaction voi dong so
 * cai, nen `TopupService` mo transaction roi goi ca repository nay lan
 * `AstriteRepository` ben trong.
 */
@Injectable()
export class TopupRepository {
  constructor(private readonly firebase: FirebaseService) {}

  private get packages() {
    return this.firebase.firestore().collection(Collections.TOPUP_PACKAGES);
  }

  private get orders() {
    return this.firebase.firestore().collection(Collections.TOPUP_ORDERS);
  }

  runTransaction<T>(fn: (tx: FirebaseFirestore.Transaction) => Promise<T>): Promise<T> {
    return this.firebase.firestore().runTransaction(fn);
  }

  // ==== Goi nap ====

  /** Toan bo goi (admin). Sort trong bo nho -> khong can composite index. */
  async listAllPackages(): Promise<TopupPackage[]> {
    const snap = await this.packages.get();
    return snap.docs
      .map((d) => this.toPackage(d.id, d.data()))
      .sort((a, b) => a.sortOrder - b.sortOrder || a.priceVnd - b.priceVnd);
  }

  /** Goi dang bat — day la danh sach app nhin thay. */
  async listActivePackages(): Promise<TopupPackage[]> {
    return (await this.listAllPackages()).filter((p) => p.isActive);
  }

  async findPackageById(packageId: string): Promise<TopupPackage | null> {
    const snap = await this.packages.doc(packageId).get();
    return snap.exists ? this.toPackage(snap.id, snap.data() ?? {}) : null;
  }

  async createPackage(pkg: Omit<TopupPackage, 'packageId'>): Promise<TopupPackage> {
    const ref = await this.packages.add(strip(pkg));
    return { packageId: ref.id, ...pkg };
  }

  async updatePackage(
    packageId: string,
    patch: Partial<Omit<TopupPackage, 'packageId'>>,
  ): Promise<void> {
    const data = strip(patch);
    if (Object.keys(data).length === 0) {
      return; // Firestore update({}) nem loi — khong co gi de sua thi thoi
    }
    await this.packages.doc(packageId).update(data);
  }

  async deletePackage(packageId: string): Promise<void> {
    await this.packages.doc(packageId).delete();
  }

  // ==== Don nap ====

  /**
   * Tao don voi doc id = `orderCode`. Tra ve `false` neu ma da ton tai (de
   * service sinh ma khac va thu lai) — KHONG ghi de don cu.
   */
  async createOrder(order: TopupOrder): Promise<boolean> {
    try {
      await this.orders.doc(String(order.orderCode)).create(strip(order));
      return true;
    } catch (e) {
      // CHI nuot ALREADY_EXISTS (gRPC code 6) — loi khac (mat mang, quyen...)
      // phai nem len, khong duoc im lang coi nhu "ma trung".
      if ((e as { code?: number }).code === 6) {
        return false;
      }
      throw e;
    }
  }

  async findOrder(orderCode: number): Promise<TopupOrder | null> {
    const snap = await this.orders.doc(String(orderCode)).get();
    return snap.exists ? this.toOrder(snap.id, snap.data() ?? {}) : null;
  }

  /** Doc don TRONG transaction — bat buoc doc truoc khi ghi (luat Firestore). */
  async getOrderInTransaction(
    tx: FirebaseFirestore.Transaction,
    orderCode: number,
  ): Promise<TopupOrder | null> {
    const snap = await tx.get(this.orders.doc(String(orderCode)));
    return snap.exists ? this.toOrder(snap.id, snap.data() ?? {}) : null;
  }

  /** Ghi trang thai don trong transaction dang mo (merge, giu nguyen field khac). */
  setOrderInTransaction(
    tx: FirebaseFirestore.Transaction,
    orderCode: number,
    patch: Partial<TopupOrder>,
  ): void {
    tx.set(this.orders.doc(String(orderCode)), strip(patch), { merge: true });
  }

  async updateOrderStatus(
    orderCode: number,
    status: TopupOrderStatus,
    extra: Partial<TopupOrder> = {},
  ): Promise<void> {
    await this.orders.doc(String(orderCode)).set({ status, ...strip(extra) }, { merge: true });
  }

  /**
   * Cap nhat cac field PHU cua don — **khong bao gio dung toi `status`**.
   *
   * Vi sao tach rieng khoi [updateOrderStatus]: ghi kem `status` o nhung cho
   * khong thuc su doi trang thai co the **keo mot don da PAID ve PENDING**, va
   * webhook goi lai sau do se cong Astrite lan hai.
   */
  async patchOrder(orderCode: number, patch: Omit<Partial<TopupOrder>, 'status'>): Promise<void> {
    const data = strip(patch);
    if (Object.keys(data).length === 0) {
      return;
    }
    await this.orders.doc(String(orderCode)).set(data, { merge: true });
  }

  /**
   * Danh dau don qua han — **chi khi doc do VAN con `PENDING`**, kiem tra ben
   * trong transaction.
   *
   * Bat buoc phai co transaction o day: neu doc-roi-ghi thi giua hai buoc,
   * webhook that co the vua chuyen don sang `PAID`; lenh ghi de se keo no ve
   * `EXPIRED`, va webhook goi lai sau do khong con thay `PAID` de chan
   * -> **cong tien lan hai**.
   *
   * Tra ve true neu that su doi trang thai.
   */
  async expireOrderIfPending(orderCode: number): Promise<boolean> {
    const ref = this.orders.doc(String(orderCode));
    return this.firebase.firestore().runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists || snap.data()?.status !== 'PENDING') {
        return false;
      }
      tx.set(ref, { status: 'EXPIRED' satisfies TopupOrderStatus }, { merge: true });
      return true;
    });
  }

  /** Lich su nap cua 1 user, moi nhat truoc (1 filter + sort bo nho, khong can index). */
  async listOrdersByUid(uid: string, limit = 50): Promise<TopupOrder[]> {
    const snap = await this.orders.where('uid', '==', uid).get();
    return snap.docs
      .map((d) => this.toOrder(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
  }

  /** Toan bo don (admin). Loc theo uid/trang thai/ngay lam trong bo nho o service. */
  async listAllOrders(): Promise<TopupOrder[]> {
    const snap = await this.orders.get();
    return snap.docs
      .map((d) => this.toOrder(d.id, d.data()))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  /**
   * Don PENDING tao truoc moc `beforeIso` — dung de danh dau EXPIRED.
   * 1 filter `status` + loc thoi gian trong bo nho: so don treo rat it.
   */
  async listStalePendingOrders(beforeIso: string): Promise<TopupOrder[]> {
    const snap = await this.orders.where('status', '==', 'PENDING').get();
    return snap.docs
      .map((d) => this.toOrder(d.id, d.data()))
      .filter((o) => o.createdAt < beforeIso);
  }

  private toPackage(packageId: string, data: FirebaseFirestore.DocumentData): TopupPackage {
    return {
      packageId,
      name: data.name ?? '',
      astrite: data.astrite ?? 0,
      priceVnd: data.priceVnd ?? 0,
      isActive: data.isActive ?? true,
      isTest: data.isTest ?? false,
      sortOrder: data.sortOrder ?? 0,
      createdAt: data.createdAt ?? '',
    };
  }

  private toOrder(docId: string, data: FirebaseFirestore.DocumentData): TopupOrder {
    return {
      orderCode: Number(data.orderCode ?? docId),
      uid: data.uid ?? '',
      packageId: data.packageId ?? '',
      packageName: data.packageName ?? '',
      astrite: data.astrite ?? 0,
      amountVnd: data.amountVnd ?? 0,
      status: (data.status as TopupOrderStatus) ?? 'PENDING',
      payosPaymentLinkId: data.payosPaymentLinkId,
      checkoutUrl: data.checkoutUrl,
      payosReference: data.payosReference,
      isSimulated: data.isSimulated,
      createdAt: data.createdAt ?? '',
      paidAt: data.paidAt,
    };
  }
}

/** Firestore tu choi gia tri `undefined` — bo han field thay vi ghi null. */
function strip<T extends object>(obj: T): Record<string, unknown> {
  return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined));
}
