import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { randomInt } from 'crypto';
import { AstriteRepository } from '../astrite/astrite.repository';
import { FramesRepository } from '../frames/frames.repository';
import { UsersRepository } from '../users/users.repository';
import {
  GACHA_COST_SINGLE,
  GACHA_COST_TEN,
  GACHA_N_ASTRITE_MAX,
  GACHA_N_ASTRITE_MIN,
  GACHA_PITY_R,
  GACHA_PITY_SR,
  GACHA_PITY_SSR,
  GACHA_RATE_R,
  GACHA_RATE_SR,
  GACHA_RATE_SSR,
  GACHA_REFUND_R,
  GACHA_REFUND_SR,
  GACHA_REFUND_SSR,
  GACHA_TEN_TIMES,
} from '../common/constants';
import { GachaPity } from '../users/entities/user.entity';
import { CreateGachaItemDto } from './dto/create-gacha-item.dto';
import { UpdateGachaItemDto } from './dto/update-gacha-item.dto';
import { GachaItem, ItemRarity, RollTier } from './entities/gacha-item.entity';
import { GachaRoll, RollOutcome, RollResultEntry, RollType } from './entities/gacha-roll.entity';
import { GachaRepository, UserGachaState } from './gacha.repository';

/** 1 dong trong trang lich su quay cua admin (kem ten nguoi quay). */
export interface AdminRollRow extends GachaRoll {
  fullName: string;
}

/** 1 user dang so huu vat pham (drawer "Ai đang sở hữu?" cua trang admin). */
export interface GachaItemOwner {
  uid: string;
  email?: string;
  fullName: string;
  avatar?: string;
}

/** Bo loc cua trang lich su quay (admin). */
export interface AdminRollFilter {
  uid?: string;
  tier?: RollTier;
  /** YYYY-MM-DD (UTC) — chi lay luot quay trong ngay do. */
  date?: string;
  limit?: number;
}

/** Nguong cong don de chon bac tu 1 so ngau nhien [0,1). */
const TIER_THRESHOLD_SSR = GACHA_RATE_SSR;
const TIER_THRESHOLD_SR = GACHA_RATE_SSR + GACHA_RATE_SR;
const TIER_THRESHOLD_R = GACHA_RATE_SSR + GACHA_RATE_SR + GACHA_RATE_R;

const PITY_LIMIT: Record<ItemRarity, number> = {
  R: GACHA_PITY_R,
  SR: GACHA_PITY_SR,
  SSR: GACHA_PITY_SSR,
};

const REFUND_BY_RARITY: Record<ItemRarity, number> = {
  R: GACHA_REFUND_R,
  SR: GACHA_REFUND_SR,
  SSR: GACHA_REFUND_SSR,
};

@Injectable()
export class GachaService {
  private readonly logger = new Logger(GachaService.name);

  constructor(
    private readonly repo: GachaRepository,
    private readonly astriteRepo: AstriteRepository,
    private readonly framesRepo: FramesRepository,
    private readonly usersRepo: UsersRepository,
  ) {}

  // ==== Nguon ngau nhien ====
  // Tach thanh 3 ham `protected` de unit test ghi de duoc (test tinh xac suat
  // bang random that thi khong on dinh). Dung `crypto.randomInt` — khong lech
  // phan phoi nhu Math.random, va day la he thong dinh toi tien that.

  /** So thuc [0,1) — do chinh xac 1e-6, du cho nguong nho nhat 0,1%. */
  protected randomFloat(): number {
    return randomInt(0, 1_000_000) / 1_000_000;
  }

  /** Chi so ngau nhien trong [0, size). */
  protected randomIndex(size: number): number {
    return randomInt(0, size);
  }

  /** So Astrite cua bac N (1..60, hai dau deu tinh). */
  protected randomAstrite(): number {
    return randomInt(GACHA_N_ASTRITE_MIN, GACHA_N_ASTRITE_MAX + 1);
  }

  private randomOf<T>(arr: T[]): T {
    return arr[this.randomIndex(arr.length)];
  }

  // ==================== Thuat toan chon bac ====================

  /**
   * Chon bac cho 1 lan quay.
   *
   * Bao hiem uu tien bac CAO truoc: neu cung luc cham pity SR va pity R thi ra
   * SR. Chi bac VUA TRUNG duoc reset bo dem (dung spec) — nen sau khi trung SSR
   * o lan thu 100, bo dem R van giu nguyen va co the ep ra R ngay lan sau.
   *
   * CHI chon bac — **khong dung vao `pity`**. Bo dem chi duoc reset khi vat pham
   * that su den tay nguoi choi (xem [roll]); neu reset ngay tai day thi truong
   * hop "bac do dang bi admin an het" se an mat bao hiem: cham moc 100 lan quay,
   * nhan Astrite thay vat pham, ma bo dem van ve 0.
   */
  private pickTier(pity: GachaPity, rnd: number): RollTier {
    if (pity.SSR >= PITY_LIMIT.SSR) return 'SSR';
    if (pity.SR >= PITY_LIMIT.SR) return 'SR';
    if (pity.R >= PITY_LIMIT.R) return 'R';
    if (rnd < TIER_THRESHOLD_SSR) return 'SSR';
    if (rnd < TIER_THRESHOLD_SR) return 'SR';
    if (rnd < TIER_THRESHOLD_R) return 'R';
    return 'N';
  }

  /** Tap vat pham da so huu tuong ung voi loai vat pham. */
  private ownedSetFor(state: UserGachaState, item: GachaItem): Set<string> {
    switch (item.itemType) {
      case 'FRAME':
        return state.unlockedFrames;
      case 'SKIN':
        return state.unlockedSkins;
      case 'EFFECT':
        return state.unlockedEffects;
    }
  }

  /**
   * Chon vat pham trong 1 bac va tinh ket qua.
   *
   * - **R**: duoc phep trung bat cu luc nao -> boc ngau nhien ca pool.
   * - **SR/SSR**: CHAC CHAN ra do chua so huu; het do moi thi moi trung.
   *
   * `state` duoc cap nhat NGAY (them vao tap da so huu) — bat buoc, neu khong
   * thi quay x10 co the phat cung 1 skin "chua so huu" o nhieu lan trong cung
   * mot luot.
   */
  private drawItem(tier: ItemRarity, pool: GachaItem[], state: UserGachaState): RollResultEntry {
    const candidates = pool.filter((i) => i.rarity === tier);
    const unowned = candidates.filter((i) => !this.ownedSetFor(state, i).has(i.refId));

    // R duoc trung tu do (boc ca pool); SR/SSR chi trung khi da so huu het bac do
    const preferUnowned = tier !== 'R';
    const picked =
      preferUnowned && unowned.length > 0 ? this.randomOf(unowned) : this.randomOf(candidates);

    const owned = this.ownedSetFor(state, picked);
    const isDuplicate = owned.has(picked.refId);
    if (!isDuplicate) {
      owned.add(picked.refId);
    }

    return {
      tier,
      itemId: picked.itemId,
      itemName: picked.itemName,
      itemType: picked.itemType,
      refId: picked.refId,
      imageUrl: picked.imageUrl,
      isDuplicate,
      refundAstrite: isDuplicate ? REFUND_BY_RARITY[tier] : 0,
    };
  }

  // ==================== Quay ====================

  async roll(uid: string, times: number): Promise<RollOutcome> {
    if (times !== 1 && times !== GACHA_TEN_TIMES) {
      throw new BadRequestException(`Chỉ hỗ trợ quay 1 hoặc ${GACHA_TEN_TIMES} lần.`);
    }
    const rollType: RollType = times === 1 ? 'SINGLE' : 'TEN';
    const cost = times === 1 ? GACHA_COST_SINGLE : GACHA_COST_TEN;

    // Doc catalog NGOAI transaction: pool it thay doi, va giu transaction ngan
    // (Firestore bat doc het truoc khi ghi — cang it doc cang it retry).
    const pool = await this.repo.listActiveItems();
    if (pool.length === 0) {
      throw new BadRequestException('Kho vật phẩm đang trống, chưa thể quay.');
    }

    const rollRef = this.repo.newRollRef();

    return this.repo.runTransaction(async (tx) => {
      const state = await this.repo.getUserStateInTransaction(tx, uid);
      if (state.astrite < cost) {
        throw new BadRequestException('Bạn không đủ Astrite.');
      }

      state.astrite -= cost;
      const results: RollResultEntry[] = [];
      let refundTotal = 0;

      for (let i = 0; i < times; i++) {
        state.pity.R++;
        state.pity.SR++;
        state.pity.SSR++;

        const tier = this.pickTier(state.pity, this.randomFloat());

        if (tier === 'N') {
          const amount = this.randomAstrite();
          refundTotal += amount;
          results.push({ tier: 'N', astriteAmount: amount, isDuplicate: false, refundAstrite: 0 });
          continue;
        }

        // Bac khong co vat pham nao dang bat (admin an het) -> ha ve N de nguoi
        // choi khong mat trang. GIU NGUYEN bo dem pity: chua nhan duoc vat pham
        // thi bao hiem chua duoc tieu — mo lai vat pham la doi thuong ngay.
        if (!pool.some((p) => p.rarity === tier)) {
          const amount = this.randomAstrite();
          refundTotal += amount;
          this.logger.warn(`Pool bac ${tier} rong -> tra Astrite thay vat pham (uid ${uid})`);
          results.push({ tier: 'N', astriteAmount: amount, isDuplicate: false, refundAstrite: 0 });
          continue;
        }

        // Chi tieu bao hiem khi CHAC CHAN co vat pham de phat
        state.pity[tier] = 0;
        const entry = this.drawItem(tier, pool, state);
        refundTotal += entry.refundAstrite;
        results.push(entry);
      }

      state.astrite += refundTotal;

      // 1 lan ghi user doc cho ca so du + pity + vat pham vua mo khoa
      this.repo.applyRollInTransaction(tx, uid, state);

      const roll: Omit<GachaRoll, 'rollId' | 'createdAt'> = {
        uid,
        rollType,
        cost,
        results,
        refundTotal,
        balanceAfter: state.astrite,
      };
      this.repo.addRollInTransaction(tx, rollRef, roll);

      // So cai: 1 dong tru chi phi + 1 dong hoan (neu co) — giu bat bien
      // "moi thay doi so du deu co dong so cai tuong ung"
      this.astriteRepo.addEntryInTransaction(tx, {
        uid,
        type: 'GACHA_SPEND',
        amount: -cost,
        balanceAfter: state.astrite - refundTotal,
        refId: rollRef.id,
      });
      if (refundTotal > 0) {
        this.astriteRepo.addEntryInTransaction(tx, {
          uid,
          type: 'GACHA_REFUND',
          amount: refundTotal,
          balanceAfter: state.astrite,
          refId: rollRef.id,
        });
      }

      return {
        rollId: rollRef.id,
        rollType,
        cost,
        results,
        refundTotal,
        astriteAfter: state.astrite,
      };
    });
  }

  // ==================== Doc ====================

  /** Catalog kem co da so huu cua chinh minh. */
  async listItemsForUser(uid: string): Promise<(GachaItem & { isOwned: boolean })[]> {
    const [items, state] = await Promise.all([
      this.repo.listActiveItems(),
      this.repo.getUserState(uid),
    ]);
    return items.map((item) => ({
      ...item,
      isOwned: this.ownedSetFor(state, item).has(item.refId),
    }));
  }

  async getRollHistory(uid: string, limit = 50): Promise<GachaRoll[]> {
    return this.repo.listRollsByUid(uid, limit);
  }

  /**
   * Trang thai man Gacha. Ti le tra ve la TI LE GOC de app tu sinh popup
   * "Rule gacha" — so hien ra luon khop voi so dang chay o server.
   * App chi hien pity SSR (user chot), nhung server van tra ca 3 cho day du.
   */
  async getState(uid: string): Promise<{
    astrite: number;
    pity: GachaPity;
    pityLimit: Record<ItemRarity, number>;
    costSingle: number;
    costTen: number;
    tenTimes: number;
    rates: { N: number; R: number; SR: number; SSR: number };
    refunds: Record<ItemRarity, number>;
  }> {
    const state = await this.repo.getUserState(uid);
    return {
      astrite: state.astrite,
      pity: state.pity,
      pityLimit: PITY_LIMIT,
      costSingle: GACHA_COST_SINGLE,
      costTen: GACHA_COST_TEN,
      tenTimes: GACHA_TEN_TIMES,
      rates: {
        N: 1 - TIER_THRESHOLD_R,
        R: GACHA_RATE_R,
        SR: GACHA_RATE_SR,
        SSR: GACHA_RATE_SSR,
      },
      refunds: REFUND_BY_RARITY,
    };
  }

  // ==================== Admin ====================

  /** Toan bo catalog ke ca vat pham dang tat (trang quan tri). */
  async listAllItems(): Promise<GachaItem[]> {
    return this.repo.listAllItems();
  }

  /**
   * Admin them vat pham. CHI cho phep `FRAME`.
   *
   * Skin va hieu ung touch co asset nam TRONG APK va khop qua `refId`
   * (= skinId / effectId trong `SkinRegistry`/`TouchEffectRegistry`). Tao moi
   * tu trang admin se sinh ra vat pham quay trung duoc nhung app khong co gi
   * de hien — nen chan thang o day thay vi de admin tu phat hien luc demo.
   */
  async createItem(dto: CreateGachaItemDto): Promise<GachaItem> {
    if (dto.itemType !== 'FRAME') {
      throw new BadRequestException(
        'Chỉ thêm mới được khung ảnh. Skin và hiệu ứng nằm trong app, chỉ sửa được thông tin.',
      );
    }
    const frame = await this.framesRepo.findById(dto.refId);
    if (!frame) {
      throw new BadRequestException('Không tìm thấy khung ảnh ứng với refId này.');
    }
    // Trung refId = 1 khung nam 2 lan trong kho -> ti le trung no cao gap doi
    if (await this.repo.findItemByRef('FRAME', dto.refId)) {
      throw new BadRequestException('Khung ảnh này đã có trong kho vật phẩm.');
    }

    return this.repo.createItem({
      itemName: dto.itemName,
      itemType: dto.itemType,
      rarity: dto.rarity,
      refId: dto.refId,
      imageUrl: dto.imageUrl ?? frame.imageUrl,
      isActive: dto.isActive ?? true,
      sortOrder: dto.sortOrder ?? 0,
      createdAt: new Date().toISOString(),
    });
  }

  /** Admin sua vat pham (`itemType`/`refId` bat bien — xem UpdateGachaItemDto). */
  async updateItem(itemId: string, dto: UpdateGachaItemDto): Promise<GachaItem> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Không tìm thấy vật phẩm.');
    }
    await this.repo.updateItem(itemId, dto);
    return { ...item, ...dto };
  }

  /**
   * Admin xoa vat pham khoi kho quay.
   * ⚠️ Chi xoa khoi KHO — ai da so huu van giu (`unlockedFrames/Skins/Effects`
   * tren user doc khong bi dong toi). Muon tam an thi dung `isActive=false`.
   */
  async deleteItem(itemId: string): Promise<GachaItem> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Không tìm thấy vật phẩm.');
    }
    await this.repo.deleteItem(itemId);
    return item;
  }

  /**
   * Vi tri + kieu gia tri cua quyen so huu tren user doc, theo loai vat pham.
   * ⚠️ SKIN/EFFECT luu dang SO (khop `SkinRegistry`/`TouchEffectRegistry` trong
   * app) — tang dang chuoi la app khong nhan ra vat pham da mo.
   */
  private ownershipKey(item: GachaItem): {
    field: 'unlockedFrames' | 'unlockedSkins' | 'unlockedEffects';
    value: string | number;
  } {
    switch (item.itemType) {
      case 'FRAME':
        return { field: 'unlockedFrames', value: item.refId };
      case 'SKIN':
        return { field: 'unlockedSkins', value: Number(item.refId) };
      case 'EFFECT':
        return { field: 'unlockedEffects', value: Number(item.refId) };
    }
  }

  /**
   * Admin TANG vat pham cho user (kho thuong — dung de demo/den bu).
   * Idempotent: tang lai vat pham da so huu thi khong doi gi.
   *
   * Khong dung AstriteService: tang khong lien quan tien, khong ghi so cai.
   */
  async grantItem(itemId: string, uid: string): Promise<GachaItem> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Không tìm thấy vật phẩm.');
    }
    // Chan go nham uid: set-merge len uid khong ton tai se sinh user doc "ma"
    if (!(await this.usersRepo.findByUid(uid))) {
      throw new NotFoundException('Không tìm thấy người dùng này.');
    }
    const { field, value } = this.ownershipKey(item);
    await this.usersRepo.unlockCollectible(uid, field, value);
    this.logger.log(`Admin tang [${item.itemType}] ${item.itemName} cho ${uid}`);
    return item;
  }

  /** Admin xem danh sach user dang so huu 1 vat pham. */
  async listItemOwners(itemId: string): Promise<{ item: GachaItem; owners: GachaItemOwner[] }> {
    const item = await this.repo.findItemById(itemId);
    if (!item) {
      throw new NotFoundException('Không tìm thấy vật phẩm.');
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

  /** Lich su quay toan he thong + loc; enrich ten nguoi quay tu uid. */
  async listAllRolls(filter: AdminRollFilter = {}): Promise<AdminRollRow[]> {
    const { uid, tier, date, limit = 200 } = filter;
    let rolls = await this.repo.listAllRolls(1000);

    if (uid) {
      rolls = rolls.filter((r) => r.uid === uid);
    }
    if (tier) {
      rolls = rolls.filter((r) => r.results.some((x) => x.tier === tier));
    }
    if (date) {
      rolls = rolls.filter((r) => r.createdAt.startsWith(date));
    }
    rolls = rolls.slice(0, limit);

    const names = await this.usersRepo.getFullNamesByUids(rolls.map((r) => r.uid));
    return rolls.map((r) => ({ ...r, fullName: names.get(r.uid) || r.uid }));
  }

  /** So luot bam nut quay: hom nay + tong (o thong ke dashboard). */
  async getRollCounts(): Promise<{ today: number; total: number }> {
    const todayStart = `${new Date().toISOString().slice(0, 10)}T00:00:00.000Z`;
    const [today, total] = await Promise.all([
      this.repo.countRollsSince(todayStart),
      this.repo.countAllRolls(),
    ]);
    return { today, total };
  }
}
