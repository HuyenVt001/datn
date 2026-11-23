import {
  BadRequestException,
  ConflictException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AstriteRepository } from '../astrite/astrite.repository';
import { Frame } from '../frames/entities/frame.entity';
import { FramesRepository } from '../frames/frames.repository';
import { UsersRepository } from '../users/users.repository';
import { CreateShopItemDto } from './dto/create-shop-item.dto';
import { UpdateShopItemDto } from './dto/update-shop-item.dto';
import {
  DEFAULT_PRICE_BY_TYPE,
  ItemType,
  ShopItem,
  ShopItemForUser,
  ShopItemOwner,
} from './entities/shop-item.entity';
import { PurchaseOutcome, ShopPurchase } from './entities/shop-purchase.entity';
import { OwnershipField, ShopRepository, UserWallet } from './shop.repository';

/** 1 dong trong trang lich su mua cua admin (kem ten nguoi mua). */
export interface AdminPurchaseRow extends ShopPurchase {
  fullName: string;
}

/** Bo loc trang lich su mua (admin). */
export interface AdminPurchaseFilter {
  uid?: string;
  itemType?: ItemType;
  /** YYYY-MM-DD (UTC). */
  date?: string;
  limit?: number;
}

/**
 * Cua hang (2026-11-15 — SHOP_PLAN.md, THAY he thong gacha).
 * Mua dut bang Astrite: gia do server quyet (client chi gui itemId), tru tien +
 * mo khoa + ghi lich su + ghi so cai trong MOT transaction.
 */
@Injectable()
export class ShopService {
  private readonly logger = new Logger(ShopService.name);

  constructor(
    private readonly repo: ShopRepository,
    private readonly astriteRepo: AstriteRepository,
    private readonly framesRepo: FramesRepository,
    private readonly usersRepo: UsersRepository,
  ) {}

  // ==================== App ====================

  /** Catalog dang mo ban + so du — ShopScreen mo len chi ton 1 request. */
  async getCatalogForUser(uid: string): Promise<{ astrite: number; items: ShopItemForUser[] }> {
    const [items, wallet] = await Promise.all([
      this.repo.listActiveItems(),
      this.repo.getWallet(uid),
    ]);
    return {
      astrite: wallet.astrite,
      items: items.map((item) => ({ ...item, isOwned: this.isOwned(wallet, item) })),
    };
  }

  async getPurchaseHistory(uid: string, limit = 50): Promise<ShopPurchase[]> {
    return this.repo.listPurchasesByUid(uid, limit);
  }

  /**
   * Mua 1 mat hang.
   * - Doc mat hang (va khung, neu la FRAME) NGOAI transaction — it doi, giu
   *   transaction ngan.
   * - Trong transaction: kiem tra da so huu / du tien tren snapshot MOI NHAT
   *   -> bam mua 2 lan lien tiep khong bi tru tien 2 lan.
   */
  async buyItem(uid: string, itemId: string): Promise<PurchaseOutcome> {
    const item = await this.repo.findItemById(itemId);
    if (!item || !item.isActive) {
      throw new BadRequestException('This item is not on sale right now.');
    }
    if (item.itemType === 'FRAME') {
      const frame = await this.framesRepo.findById(item.refId);
      if (!frame || frame.unlockType !== 'SHOP') {
        throw new BadRequestException('This item is not on sale right now.');
      }
    }

    const { field, value } = this.ownershipKey(item);
    const purchaseRef = this.repo.newPurchaseRef();

    return this.repo.runTransaction(async (tx) => {
      const wallet = await this.repo.getWalletInTransaction(tx, uid);
      if (!wallet.exists) {
        throw new NotFoundException('User not found.');
      }
      if (this.isOwned(wallet, item)) {
        throw new ConflictException('You already own this item.');
      }
      if (wallet.astrite < item.price) {
        throw new BadRequestException("You don't have enough Astrite.");
      }

      const balanceAfter = wallet.astrite - item.price;
      const purchase: Omit<ShopPurchase, 'purchaseId'> = {
        uid,
        itemId: item.itemId,
        itemName: item.itemName,
        itemType: item.itemType,
        refId: item.refId,
        price: item.price,
        balanceAfter,
        createdAt: new Date().toISOString(),
      };

      this.astriteRepo.setBalanceInTransaction(tx, uid, balanceAfter);
      this.repo.unlockInTransaction(tx, uid, field, value);
      this.repo.addPurchaseInTransaction(tx, purchaseRef, purchase);
      // Giu bat bien "moi thay doi so du deu co 1 dong so cai" — ke ca mon gia 0
      this.astriteRepo.addEntryInTransaction(tx, {
        uid,
        type: 'SHOP_PURCHASE',
        amount: -item.price,
        balanceAfter,
        refId: purchaseRef.id,
      });

      this.logger.log(`${uid} mua [${item.itemType}] ${item.itemName} gia ${item.price}`);
      return { purchase: { purchaseId: purchaseRef.id, ...purchase }, astriteAfter: balanceAfter };
    });
  }

  // ==================== Admin ====================

  async listAllItems(): Promise<ShopItem[]> {
    return this.repo.listAllItems();
  }

  /**
   * Admin niem yet mat hang moi.
   * - FRAME: khung phai ton tai va co unlockType = SHOP (khung moc streak /
   *   so bai... mo khoa theo dieu kien, khong ban).
   * - SKIN / EFFECT: refId la so nguyen >= 1 (0 = Default / None, luon so huu).
   *   Server khong biet APK co id do khong — app tu an mat hang la.
   * - Chong trung (itemType, refId).
   */
  async createItem(dto: CreateShopItemDto): Promise<ShopItem> {
    const refId = this.normalizeRefId(dto.itemType, dto.refId);
    let frame: Frame | null = null;
    if (dto.itemType === 'FRAME') {
      frame = await this.framesRepo.findById(refId);
      if (!frame) {
        throw new BadRequestException('No frame found for this refId.');
      }
      if (frame.unlockType !== 'SHOP') {
        throw new BadRequestException(
          'Only frames with the "Sold in Shop" (SHOP) unlock condition can be sold.',
        );
      }
    }
    if (await this.repo.findItemByRef(dto.itemType, refId)) {
      throw new ConflictException('This item is already listed in the Shop.');
    }

    return this.repo.createItem({
      itemName: dto.itemName,
      itemType: dto.itemType,
      refId,
      price: dto.price ?? DEFAULT_PRICE_BY_TYPE[dto.itemType],
      imageUrl: dto.imageUrl ?? frame?.imageUrl,
      isActive: dto.isActive ?? true,
      sortOrder: dto.sortOrder ?? 0,
      createdAt: new Date().toISOString(),
    });
  }

  /** Admin sua mat hang (`itemType`/`refId` bat bien — xem UpdateShopItemDto). */
  async updateItem(itemId: string, dto: UpdateShopItemDto): Promise<ShopItem> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Item not found.');
    }
    await this.repo.updateItem(itemId, dto);
    const patch = Object.fromEntries(Object.entries(dto).filter(([, v]) => v !== undefined));
    return { ...item, ...patch };
  }

  /** Go mat hang khoi Shop. Ai da mua van giu (khong dung toi `unlocked*`). */
  async deleteItem(itemId: string): Promise<ShopItem> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Item not found.');
    }
    await this.repo.deleteItem(itemId);
    return item;
  }

  /**
   * Admin TANG vat pham cho user (demo / den bu). Idempotent.
   * Khong lien quan Astrite -> khong ghi so cai, khong ghi shopPurchases.
   */
  async grantItem(itemId: string, uid: string): Promise<ShopItem> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Item not found.');
    }
    // Chan go nham uid: set-merge len uid khong ton tai se sinh user doc "ma"
    if (!(await this.usersRepo.findByUid(uid))) {
      throw new NotFoundException('User not found.');
    }
    const { field, value } = this.ownershipKey(item);
    await this.usersRepo.unlockCollectible(uid, field, value);
    this.logger.log(`Admin gifted [${item.itemType}] ${item.itemName} to ${uid}`);
    return item;
  }

  async listItemOwners(itemId: string): Promise<{ item: ShopItem; owners: ShopItemOwner[] }> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Item not found.');
    }
    const { field, value } = this.ownershipKey(item);
    const users = await this.usersRepo.listByCollectible(field, value);
    return {
      item,
      owners: users.map((u) => ({
        uid: u.uid,
        email: u.email || undefined,
        fullName: u.fullName,
        avatar: u.avatar,
      })),
    };
  }

  /** Lich su mua toan he thong + loc (trong bo nho — quy mo DATN); enrich ten nguoi mua. */
  async listAllPurchases(filter: AdminPurchaseFilter = {}): Promise<AdminPurchaseRow[]> {
    const { uid, itemType, date, limit = 200 } = filter;
    let rows = await this.repo.listAllPurchases(1000);
    if (uid) {
      rows = rows.filter((r) => r.uid === uid);
    }
    if (itemType) {
      rows = rows.filter((r) => r.itemType === itemType);
    }
    if (date) {
      rows = rows.filter((r) => r.createdAt.startsWith(date));
    }
    rows = rows.slice(0, limit);

    const names = await this.usersRepo.getFullNamesByUids(rows.map((r) => r.uid));
    return rows.map((r) => ({ ...r, fullName: names.get(r.uid) || r.uid }));
  }

  /** So luot mua: hom nay + tong (dashboard admin). */
  async getPurchaseCounts(): Promise<{ today: number; total: number }> {
    const todayStart = `${new Date().toISOString().slice(0, 10)}T00:00:00.000Z`;
    const [today, total] = await Promise.all([
      this.repo.countPurchasesSince(todayStart),
      this.repo.countAllPurchases(),
    ]);
    return { today, total };
  }

  // ==================== Dong bo voi khung anh ====================

  /**
   * FramesService goi khi tao/sua/xoa khung (best-effort o phia goi):
   * `listed=true` -> dam bao khung co mat hang (tao moi gia mac dinh neu chua
   * co — KHONG ghi de gia/ten admin da sua); `false` -> go mat hang neu co.
   */
  async syncFrameListing(frame: Frame, listed: boolean): Promise<void> {
    const existing = await this.repo.findItemByRef('FRAME', frame.frameId);
    if (listed) {
      if (existing) {
        return;
      }
      await this.repo.createItem({
        itemName: frame.frameName,
        itemType: 'FRAME',
        refId: frame.frameId,
        price: DEFAULT_PRICE_BY_TYPE.FRAME,
        imageUrl: frame.imageUrl,
        isActive: true,
        sortOrder: 0,
        createdAt: new Date().toISOString(),
      });
    } else if (existing) {
      await this.repo.deleteItem(existing.itemId);
    }
  }

  // ==================== Helper ====================

  /**
   * Vi tri + kieu gia tri quyen so huu tren user doc.
   * ⚠️ SKIN/EFFECT luu dang SO (khop registry trong app) — ghi chuoi la app
   * khong nhan ra vat pham da mua.
   */
  private ownershipKey(item: ShopItem): { field: OwnershipField; value: string | number } {
    switch (item.itemType) {
      case 'FRAME':
        return { field: 'unlockedFrames', value: item.refId };
      case 'SKIN':
        return { field: 'unlockedSkins', value: Number(item.refId) };
      case 'EFFECT':
        return { field: 'unlockedEffects', value: Number(item.refId) };
    }
  }

  private isOwned(wallet: UserWallet, item: ShopItem): boolean {
    switch (item.itemType) {
      case 'FRAME':
        return wallet.unlockedFrames.has(item.refId);
      case 'SKIN':
        return wallet.unlockedSkins.has(String(Number(item.refId)));
      case 'EFFECT':
        return wallet.unlockedEffects.has(String(Number(item.refId)));
    }
  }

  /** SKIN/EFFECT: refId phai la so nguyen >= 1, chuan hoa "01" -> "1". */
  private normalizeRefId(itemType: ItemType, raw: string): string {
    const trimmed = raw.trim();
    if (itemType === 'FRAME') {
      return trimmed;
    }
    const id = Number(trimmed);
    if (!Number.isInteger(id) || id < 1) {
      throw new BadRequestException(
        'Skin / effect id must be an integer ≥ 1 (0 is the default, always owned).',
      );
    }
    return String(id);
  }
}
