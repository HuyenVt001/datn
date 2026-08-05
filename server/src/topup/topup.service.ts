import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Webhook, WebhookData } from '@payos/node';
import { AstriteRepository } from '../astrite/astrite.repository';
import { PAYOS_DESCRIPTION_MAX, TOPUP_ORDER_TTL_MINUTES } from '../common/constants';
import { UsersRepository } from '../users/users.repository';
import { AdminTopupsDto } from './dto/admin-topups.dto';
import { CreateTopupPackageDto } from './dto/create-package.dto';
import { SimulateTopupDto } from './dto/simulate-topup.dto';
import { UpdateTopupPackageDto } from './dto/update-package.dto';
import { AdminTopupRow, TopupOrder, TopupRevenueSummary } from './entities/topup-order.entity';
import { TopupPackage } from './entities/topup-package.entity';
import { PayosService } from './payos.service';
import { TopupRepository } from './topup.repository';

/** Ket qua tra ve cho app sau khi tao don. */
export interface CreatedOrder {
  orderCode: number;
  checkoutUrl: string;
  amountVnd: number;
  astrite: number;
  packageName: string;
}

/** Ket qua xu ly 1 lan webhook — dung de log va de test doc duoc. */
export type WebhookOutcome =
  | 'CREDITED' // cong Astrite thanh cong (lan dau tien)
  | 'ALREADY_PAID' // don da PAID roi -> bo qua (idempotent)
  | 'UNKNOWN_ORDER' // khong co don nay (vd payload thu luc dang ky webhook)
  | 'AMOUNT_MISMATCH' // so tien bao ve khac so tien cua don -> KHONG cong
  | 'NOT_SUCCESS'; // PayOS bao giao dich khong thanh cong

@Injectable()
export class TopupService {
  private readonly logger = new Logger(TopupService.name);

  constructor(
    private readonly repo: TopupRepository,
    private readonly astriteRepo: AstriteRepository,
    private readonly usersRepo: UsersRepository,
    private readonly payos: PayosService,
    private readonly config: ConfigService,
  ) {}

  // ==================== Goi nap ====================

  /** Goi app nhin thay (chi goi dang bat). */
  listPackagesForApp(): Promise<TopupPackage[]> {
    return this.repo.listActivePackages();
  }

  listAllPackages(): Promise<TopupPackage[]> {
    return this.repo.listAllPackages();
  }

  async createPackage(dto: CreateTopupPackageDto): Promise<TopupPackage> {
    return this.repo.createPackage({
      name: dto.name,
      astrite: dto.astrite,
      priceVnd: dto.priceVnd,
      isActive: dto.isActive ?? true,
      isTest: dto.isTest ?? false,
      sortOrder: dto.sortOrder ?? 0,
      createdAt: new Date().toISOString(),
    });
  }

  async updatePackage(packageId: string, dto: UpdateTopupPackageDto): Promise<TopupPackage> {
    await this.requirePackage(packageId);
    await this.repo.updatePackage(packageId, dto);
    return this.requirePackage(packageId);
  }

  /**
   * Xoa goi. Don da tao van giu nguyen (da chup lai ten/gia/so Astrite cua
   * chinh no), nen lich su nap khong bi vo khi xoa goi cu.
   */
  async deletePackage(packageId: string): Promise<TopupPackage> {
    const pkg = await this.requirePackage(packageId);
    await this.repo.deletePackage(packageId);
    return pkg;
  }

  // ==================== Tao don ====================

  /**
   * Tao don nap + link thanh toan PayOS.
   *
   * Thu tu co chu y: GHI DON TRUOC, goi PayOS SAU. Neu goi PayOS truoc ma ghi
   * don that bai thi se ton tai link thanh toan khong co don doi ung — nguoi
   * dung tra tien xong webhook ve khong biet cong cho ai.
   */
  async createOrder(uid: string, packageId: string): Promise<CreatedOrder> {
    const pkg = await this.requirePackage(packageId);
    if (!pkg.isActive) {
      throw new BadRequestException('Gói nạp này hiện không khả dụng.');
    }

    const order = await this.insertOrder(uid, pkg);

    let link: { checkoutUrl: string; paymentLinkId: string };
    try {
      const created = await this.payos.createPaymentLink({
        orderCode: order.orderCode,
        amountVnd: order.amountVnd,
        description: buildDescription(pkg.astrite),
        expiresAtUnixSeconds: Math.floor(Date.now() / 1000) + TOPUP_ORDER_TTL_MINUTES * 60,
      });
      link = { checkoutUrl: created.checkoutUrl, paymentLinkId: created.paymentLinkId };
    } catch (e) {
      // Link khong tao duoc -> don nay se khong bao gio thanh toan duoc.
      // Danh dau CANCELLED ngay de no khong nam mai o trang thai PENDING.
      await this.repo
        .updateOrderStatus(order.orderCode, 'CANCELLED')
        .catch((err) =>
          this.logger.error(`Khong danh dau duoc don ${order.orderCode} la CANCELLED: ${err}`),
        );
      this.logger.error(`Tao link PayOS that bai cho don ${order.orderCode}: ${describe(e)}`);
      throw e;
    }

    await this.repo.updateOrderStatus(order.orderCode, 'PENDING', {
      checkoutUrl: link.checkoutUrl,
      payosPaymentLinkId: link.paymentLinkId,
    });
    this.logger.log(`Tao don nap ${order.orderCode} (${order.amountVnd}d) cho ${uid}`);

    return {
      orderCode: order.orderCode,
      checkoutUrl: link.checkoutUrl,
      amountVnd: order.amountVnd,
      astrite: order.astrite,
      packageName: order.packageName,
    };
  }

  /** Trang thai don — app poll ham nay sau khi nguoi dung dong trang thanh toan. */
  async getOrderForUser(uid: string, orderCode: number): Promise<TopupOrder> {
    const order = await this.repo.findOrder(orderCode);
    if (!order) {
      throw new NotFoundException('Không tìm thấy đơn nạp này.');
    }
    if (order.uid !== uid) {
      // Ma don la so doan duoc -> phai chan doc don cua nguoi khac.
      throw new ForbiddenException('Đơn nạp này không phải của bạn.');
    }
    return order;
  }

  /**
   * Lich su nap cua chinh minh. Tien the don qua han sang EXPIRED o day: quy mo
   * DATN khong dung `@nestjs/schedule` (them dependency), va nguoi dung mo lich
   * su la luc duy nhat can thay trang thai dung.
   */
  async listHistory(uid: string, limit = 50): Promise<TopupOrder[]> {
    await this.expireStaleOrders();
    return this.repo.listOrdersByUid(uid, limit);
  }

  // ==================== Webhook (PayOS goi) ====================

  /**
   * Diem vao cua webhook PayOS. **Day la NOI DUY NHAT cong Astrite tu nap tien.**
   *
   * Luon tra 200 tru khi chu ky sai (401):
   *  - Luc dang ky webhook, PayOS gui 1 payload THU voi `orderCode` khong co
   *    that. Tra 404 o day thi PayOS bao "webhook khong hop le" va khong luu URL.
   *  - Tra 4xx/5xx cho payload that se khien PayOS goi lai lien tuc.
   */
  async handleWebhook(body: Webhook): Promise<{ received: true; outcome: WebhookOutcome }> {
    if (!this.payos.isConfigured) {
      // Khong co khoa checksum thi khong the phan biet that/gia -> tu choi han.
      this.logger.error('Nhan webhook nhung server chua cau hinh khoa PayOS.');
      throw new UnauthorizedException('Webhook chưa được cấu hình.');
    }

    let data: WebhookData;
    try {
      data = await this.payos.verifyWebhook(body);
    } catch (e) {
      // KHONG log body: chua chu ky va thong tin giao dich.
      this.logger.warn(`Webhook bi tu choi vi chu ky khong hop le: ${describe(e)}`);
      throw new UnauthorizedException('Chữ ký webhook không hợp lệ.');
    }

    const outcome = await this.applyVerifiedWebhook(data);
    return { received: true, outcome };
  }

  /**
   * Xu ly du lieu webhook DA verify chu ky.
   * Tach rieng khoi [handleWebhook] de `/topup/simulate` dung lai duoc khi may
   * dev chua co khoa checksum.
   */
  async applyVerifiedWebhook(data: WebhookData): Promise<WebhookOutcome> {
    // SDK chi kiem tra toan ven du lieu, KHONG kiem tra giao dich thanh cong.
    if (data.code !== '00') {
      this.logger.warn(`Webhook don ${data.orderCode}: giao dich khong thanh cong (${data.code}).`);
      return 'NOT_SUCCESS';
    }

    const outcome = await this.creditOrderOnce(data);
    switch (outcome) {
      case 'CREDITED':
        this.logger.log(`Don ${data.orderCode}: da cong Astrite.`);
        break;
      case 'ALREADY_PAID':
        // Duong nhien xay ra: PayOS goi lai khi timeout mang. Khong phai loi.
        this.logger.log(`Don ${data.orderCode}: da cong tu truoc, bo qua (idempotent).`);
        break;
      case 'UNKNOWN_ORDER':
        this.logger.log(`Webhook cho ma don khong co trong he thong: ${data.orderCode}.`);
        break;
      case 'AMOUNT_MISMATCH':
        // Tien that -> khong tu doan. Giu don PENDING de admin doi soat tay.
        this.logger.error(
          `Don ${data.orderCode}: so tien bao ve (${data.amount}) khac so tien cua don. ` +
            'KHONG cong Astrite — can doi soat thu cong.',
        );
        break;
      default:
        break;
    }
    return outcome;
  }

  /**
   * Cong Astrite DUNG MOT LAN cho 1 don, trong 1 transaction Firestore.
   *
   * Chan cong 2 lan bang chinh trang thai don: doc doc trong transaction, thay
   * `PAID` la dung. Firestore serialize cac transaction cham cung doc nen 2
   * webhook den dong thoi khong the cung thay `PENDING`.
   *
   * ⚠️ Chi `PAID` moi chan. `EXPIRED` VAN cong: EXPIRED la phong doan cua server
   * (het TTL), con webhook la su that tu PayOS — tien da vao tai khoan that roi.
   * Tha cong muon con hon nguoi dung mat tien ma khong nhan duoc gi.
   */
  private async creditOrderOnce(data: WebhookData): Promise<WebhookOutcome> {
    return this.repo.runTransaction(async (tx) => {
      const order = await this.repo.getOrderInTransaction(tx, data.orderCode);
      if (!order) {
        return 'UNKNOWN_ORDER';
      }
      if (order.status === 'PAID') {
        return 'ALREADY_PAID';
      }
      if (data.amount !== order.amountVnd) {
        return 'AMOUNT_MISMATCH';
      }
      if (order.status !== 'PENDING') {
        this.logger.warn(
          `Don ${order.orderCode} dang o trang thai ${order.status} nhung PayOS bao da tra — van cong.`,
        );
      }

      // Doc HET roi moi ghi (rang buoc cua transaction Firestore).
      const balance =
        (await this.astriteRepo.getBalanceInTransaction(tx, order.uid)) + order.astrite;

      this.astriteRepo.setBalanceInTransaction(tx, order.uid, balance);
      this.astriteRepo.addEntryInTransaction(tx, {
        uid: order.uid,
        type: 'TOPUP',
        amount: order.astrite,
        balanceAfter: balance,
        refId: String(order.orderCode),
      });
      this.repo.setOrderInTransaction(tx, order.orderCode, {
        status: 'PAID',
        paidAt: new Date().toISOString(),
        payosReference: data.reference,
        payosPaymentLinkId: data.paymentLinkId,
      });
      return 'CREDITED';
    });
  }

  // ==================== Gia lap (chi moi truong dev) ====================

  /**
   * Gia lap PayOS bao "da thanh toan" — tiet kiem han muc 100 giao dich cua goi
   * FREE-100 (GACHA_PLAN.md muc 4.5). Xem [SimulateTopupDto] de biet 2 cach dung.
   */
  async simulate(
    uid: string,
    dto: SimulateTopupDto,
  ): Promise<{ orderCode: number; outcome: WebhookOutcome }> {
    if (this.config.get<string>('NODE_ENV') === 'production') {
      throw new ForbiddenException('Endpoint giả lập chỉ chạy ở môi trường dev.');
    }

    let orderCode = dto.orderCode;
    if (!orderCode) {
      if (!dto.packageId) {
        throw new BadRequestException(
          'Cần truyền packageId (tạo đơn mới) hoặc orderCode (phát lại).',
        );
      }
      const pkg = await this.requirePackage(dto.packageId);
      const order = await this.insertOrder(uid, pkg, { isSimulated: true });
      orderCode = order.orderCode;
    }

    const order = await this.repo.findOrder(orderCode);
    if (!order) {
      throw new NotFoundException('Không tìm thấy đơn nạp này.');
    }

    const data = buildFakeWebhookData(order);
    if (this.payos.canSign) {
      // Co khoa checksum -> di dung duong that: ky roi verify lai.
      const signature = await this.payos.signWebhookData(data);
      const result = await this.handleWebhook({
        code: '00',
        desc: 'success',
        success: true,
        data,
        signature,
      });
      return { orderCode, outcome: result.outcome };
    }

    this.logger.warn('Chua co PAYOS_CHECKSUM_KEY — gia lap bo qua buoc verify chu ky.');
    return { orderCode, outcome: await this.applyVerifiedWebhook(data) };
  }

  // ==================== Admin ====================

  /** Toan bo don + thong ke doanh thu, loc trong bo nho (quy mo DATN). */
  async listAllOrders(
    filter: AdminTopupsDto,
  ): Promise<{ rows: AdminTopupRow[]; summary: TopupRevenueSummary }> {
    await this.expireStaleOrders();
    const all = await this.repo.listAllOrders();

    const matched = all.filter(
      (o) =>
        (!filter.uid || o.uid === filter.uid) &&
        (!filter.status || o.status === filter.status) &&
        (!filter.date || o.createdAt.slice(0, 10) === filter.date),
    );

    const rows = matched.slice(0, filter.limit ?? 200);
    const names = await this.usersRepo.getFullNamesByUids([...new Set(rows.map((o) => o.uid))]);

    return {
      rows: rows.map((o) => ({ ...o, fullName: names.get(o.uid) ?? '' })),
      // Thong ke tren TOAN BO tap da loc, khong phai tren `rows` da cat bot —
      // nguoc lai doanh thu se giam di khi dat limit nho.
      summary: summarize(matched),
    };
  }

  // ==================== Ho tro ====================

  private async requirePackage(packageId: string): Promise<TopupPackage> {
    const pkg = await this.repo.findPackageById(packageId);
    if (!pkg) {
      throw new NotFoundException('Không tìm thấy gói nạp này.');
    }
    return pkg;
  }

  /**
   * Ghi doc don voi ma tu sinh. `orderCode` phai la SO NGUYEN (PayOS bat buoc)
   * va duy nhat toan he thong; dung `Date.now()` (mili giay) + 2 chu so ngau
   * nhien roi dua vao `create()` de Firestore tu bao trung.
   */
  private async insertOrder(
    uid: string,
    pkg: TopupPackage,
    extra: Partial<TopupOrder> = {},
  ): Promise<TopupOrder> {
    for (let attempt = 0; attempt < 5; attempt++) {
      const order: TopupOrder = {
        orderCode: Date.now() * 100 + Math.floor(Math.random() * 100),
        uid,
        packageId: pkg.packageId,
        packageName: pkg.name,
        astrite: pkg.astrite,
        amountVnd: pkg.priceVnd,
        status: 'PENDING',
        createdAt: new Date().toISOString(),
        ...extra,
      };
      if (await this.repo.createOrder(order)) {
        return order;
      }
    }
    throw new BadRequestException('Không tạo được đơn nạp, vui lòng thử lại.');
  }

  /**
   * Danh dau don PENDING qua han thanh EXPIRED — de trang lich su khong day
   * don treo. Khong lam mat tien: [creditOrderOnce] van cong cho don EXPIRED
   * neu webhook that ve muon.
   */
  private async expireStaleOrders(): Promise<void> {
    const cutoff = new Date(Date.now() - TOPUP_ORDER_TTL_MINUTES * 60_000).toISOString();
    const stale = await this.repo.listStalePendingOrders(cutoff);
    for (const order of stale) {
      await this.repo
        .updateOrderStatus(order.orderCode, 'EXPIRED')
        .catch((e) =>
          this.logger.warn(`Khong danh dau duoc don ${order.orderCode}: ${describe(e)}`),
        );
    }
  }
}

/**
 * Noi dung chuyen khoan hien tren app ngan hang. PayOS gioi han 25 ky tu —
 * dai hon la API tra 400 va nguoi dung khong nap duoc.
 */
function buildDescription(astrite: number): string {
  return `SNAPGET ${astrite}`.slice(0, PAYOS_DESCRIPTION_MAX);
}

/** Payload webhook gia lap cho `/topup/simulate` — dung dang PayOS that gui. */
function buildFakeWebhookData(order: TopupOrder): WebhookData {
  return {
    orderCode: order.orderCode,
    amount: order.amountVnd,
    description: buildDescription(order.astrite),
    accountNumber: '0000000000',
    reference: `SIMULATED-${order.orderCode}`,
    transactionDateTime: new Date().toISOString(),
    currency: 'VND',
    paymentLinkId: order.payosPaymentLinkId ?? `simulated-${order.orderCode}`,
    code: '00',
    desc: 'success',
  };
}

/** Gom thong ke doanh thu cho trang admin. */
function summarize(orders: TopupOrder[]): TopupRevenueSummary {
  const paid = orders.filter((o) => o.status === 'PAID');
  const byDate = new Map<string, { revenueVnd: number; count: number }>();
  for (const o of paid) {
    const date = (o.paidAt ?? o.createdAt).slice(0, 10);
    const cur = byDate.get(date) ?? { revenueVnd: 0, count: 0 };
    byDate.set(date, { revenueVnd: cur.revenueVnd + o.amountVnd, count: cur.count + 1 });
  }
  return {
    paidRevenueVnd: paid.reduce((sum, o) => sum + o.amountVnd, 0),
    paidCount: paid.length,
    pendingCount: orders.filter((o) => o.status === 'PENDING').length,
    paidAstrite: paid.reduce((sum, o) => sum + o.astrite, 0),
    byDate: [...byDate.entries()]
      .map(([date, v]) => ({ date, ...v }))
      .sort((a, b) => b.date.localeCompare(a.date)),
  };
}

/** Lay message cua loi de log — KHONG bao gio in ca object (co the chua khoa). */
function describe(e: unknown): string {
  return e instanceof Error ? e.message : 'loi khong xac dinh';
}
