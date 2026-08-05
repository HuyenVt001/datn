import { BadRequestException, ForbiddenException, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { WebhookData } from '@payos/node';
import { AstriteRepository } from '../astrite/astrite.repository';
import { NewAstriteTransaction } from '../astrite/entities/astrite-transaction.entity';
import { UsersRepository } from '../users/users.repository';
import { TopupOrder } from './entities/topup-order.entity';
import { TopupPackage } from './entities/topup-package.entity';
import { PayosService } from './payos.service';
import { TopupRepository } from './topup.repository';
import { TopupService } from './topup.service';

/**
 * Nap Astrite dung TIEN THAT — sai o day khong phai bug du lieu ma la chenh
 * lech tien that <-> Astrite da phat, va KHONG hoan tac duoc.
 *
 * Cac test duoi khoa chat 5 dieu:
 *   1. Webhook goi lai bao nhieu lan cung chi cong Astrite DUNG 1 lan.
 *   2. Chu ky sai -> 401, khong cong gi.
 *   3. So tien bao ve khac so tien cua don -> KHONG cong.
 *   4. Don da het han (EXPIRED) VAN duoc cong neu PayOS bao da tra —
 *      EXPIRED la phong doan cua server, con webhook la su that.
 *   5. Ma don khong ton tai -> tra 200 (payload thu luc dang ky webhook).
 */

const PKG: TopupPackage = {
  packageId: 'p1',
  name: '600 Astrite',
  astrite: 600,
  priceVnd: 5_000,
  isActive: true,
  isTest: false,
  sortOrder: 1,
  createdAt: '2026-08-05T00:00:00.000Z',
};

const webhookData = (over: Partial<WebhookData> = {}): WebhookData => ({
  orderCode: 111,
  amount: 5_000,
  description: 'SNAPGET 600',
  accountNumber: '0000000000',
  reference: 'FT123',
  transactionDateTime: '2026-08-05T00:00:00.000Z',
  currency: 'VND',
  paymentLinkId: 'link-1',
  code: '00',
  desc: 'success',
  ...over,
});

describe('TopupService', () => {
  let service: TopupService;
  let repo: jest.Mocked<TopupRepository>;
  let astriteRepo: jest.Mocked<AstriteRepository>;
  let usersRepo: jest.Mocked<UsersRepository>;
  let payos: jest.Mocked<PayosService>;
  let config: ConfigService;

  /** Kho don trong bo nho — dung de kiem tra idempotent that su. */
  let orders: Map<number, TopupOrder>;
  let balance: number;
  let ledger: NewAstriteTransaction[];
  let nodeEnv: string;

  const fakeTx = {} as FirebaseFirestore.Transaction;

  const putOrder = (over: Partial<TopupOrder> = {}): TopupOrder => {
    const order: TopupOrder = {
      orderCode: 111,
      uid: 'u1',
      packageId: PKG.packageId,
      packageName: PKG.name,
      astrite: PKG.astrite,
      amountVnd: PKG.priceVnd,
      status: 'PENDING',
      createdAt: '2026-08-05T00:00:00.000Z',
      ...over,
    };
    orders.set(order.orderCode, order);
    return order;
  };

  beforeEach(() => {
    orders = new Map();
    balance = 1_000;
    ledger = [];
    nodeEnv = 'development';

    repo = {
      listActivePackages: jest.fn(async () => [PKG]),
      listAllPackages: jest.fn(async () => [PKG]),
      findPackageById: jest.fn(async (id: string) => (id === PKG.packageId ? PKG : null)),
      createPackage: jest.fn(),
      updatePackage: jest.fn(),
      deletePackage: jest.fn(),
      createOrder: jest.fn(async (o: TopupOrder) => {
        if (orders.has(o.orderCode)) {
          return false;
        }
        orders.set(o.orderCode, o);
        return true;
      }),
      findOrder: jest.fn(async (code: number) => orders.get(code) ?? null),
      getOrderInTransaction: jest.fn(async (_tx, code: number) => orders.get(code) ?? null),
      setOrderInTransaction: jest.fn((_tx, code: number, patch: Partial<TopupOrder>) => {
        const cur = orders.get(code);
        if (cur) {
          orders.set(code, { ...cur, ...patch });
        }
      }),
      updateOrderStatus: jest.fn(async (code: number, status, extra = {}) => {
        const cur = orders.get(code);
        if (cur) {
          orders.set(code, { ...cur, status, ...extra });
        }
      }),
      listOrdersByUid: jest.fn(async () => [...orders.values()]),
      listAllOrders: jest.fn(async () => [...orders.values()]),
      listStalePendingOrders: jest.fn(async () => []),
      runTransaction: jest.fn((fn: (tx: FirebaseFirestore.Transaction) => Promise<unknown>) =>
        fn(fakeTx),
      ),
    } as unknown as jest.Mocked<TopupRepository>;

    astriteRepo = {
      getBalanceInTransaction: jest.fn(async () => balance),
      setBalanceInTransaction: jest.fn((_tx, _uid, next: number) => {
        balance = next;
      }),
      addEntryInTransaction: jest.fn((_tx, e: NewAstriteTransaction) => {
        ledger.push(e);
      }),
    } as unknown as jest.Mocked<AstriteRepository>;

    usersRepo = {
      getFullNamesByUids: jest.fn(async () => new Map([['u1', 'Nguyen Van An']])),
    } as unknown as jest.Mocked<UsersRepository>;

    payos = {
      isConfigured: true,
      canSign: true,
      createPaymentLink: jest.fn(async () => ({
        checkoutUrl: 'https://pay.payos.vn/web/link-1',
        paymentLinkId: 'link-1',
      })),
      verifyWebhook: jest.fn(async (body: { data: WebhookData }) => body.data),
      signWebhookData: jest.fn(async () => 'chu-ky-gia-lap'),
    } as unknown as jest.Mocked<PayosService>;

    config = { get: jest.fn(() => nodeEnv) } as unknown as ConfigService;

    service = new TopupService(repo, astriteRepo, usersRepo, payos, config);
  });

  /** Goi webhook nhu PayOS goi that (co buoc verify chu ky). */
  const callWebhook = (data: WebhookData) =>
    service.handleWebhook({ code: '00', desc: 'success', success: true, data, signature: 'sig' });

  describe('webhook idempotent (chống cộng tiền 2 lần)', () => {
    it('gọi 3 lần cùng orderCode -> số dư chỉ tăng 1 lần, sổ cái chỉ 1 dòng', async () => {
      putOrder();

      const first = await callWebhook(webhookData());
      const second = await callWebhook(webhookData());
      const third = await callWebhook(webhookData());

      expect(first.outcome).toBe('CREDITED');
      expect(second.outcome).toBe('ALREADY_PAID');
      expect(third.outcome).toBe('ALREADY_PAID');
      expect(balance).toBe(1_000 + PKG.astrite);
      expect(ledger).toHaveLength(1);
      expect(ledger[0]).toMatchObject({ type: 'TOPUP', amount: PKG.astrite, refId: '111' });
    });

    it('đơn chuyển sang PAID kèm mã giao dịch ngân hàng để đối soát', async () => {
      putOrder();

      await callWebhook(webhookData({ reference: 'FT-9999' }));

      const saved = orders.get(111);
      expect(saved?.status).toBe('PAID');
      expect(saved?.payosReference).toBe('FT-9999');
      expect(saved?.paidAt).toBeTruthy();
    });
  });

  describe('webhook giả mạo / dữ liệu lạ', () => {
    it('chữ ký sai -> 401 và không cộng gì', async () => {
      putOrder();
      payos.verifyWebhook.mockRejectedValueOnce(new Error('Data not integrity'));

      await expect(callWebhook(webhookData())).rejects.toBeInstanceOf(UnauthorizedException);
      expect(balance).toBe(1_000);
      expect(ledger).toHaveLength(0);
    });

    it('chưa cấu hình khoá PayOS -> 401, không thể phân biệt thật/giả', async () => {
      Object.defineProperty(payos, 'isConfigured', { get: () => false });

      await expect(callWebhook(webhookData())).rejects.toBeInstanceOf(UnauthorizedException);
    });

    it('số tiền báo về khác số tiền của đơn -> KHÔNG cộng, đơn giữ nguyên PENDING', async () => {
      putOrder();

      const res = await callWebhook(webhookData({ amount: 1_000 }));

      expect(res.outcome).toBe('AMOUNT_MISMATCH');
      expect(balance).toBe(1_000);
      expect(orders.get(111)?.status).toBe('PENDING');
    });

    it('PayOS báo giao dịch không thành công (code != 00) -> không cộng', async () => {
      putOrder();

      const res = await callWebhook(webhookData({ code: '01' }));

      expect(res.outcome).toBe('NOT_SUCCESS');
      expect(balance).toBe(1_000);
    });

    it('mã đơn không tồn tại -> vẫn trả 200 (payload thử lúc đăng ký webhook)', async () => {
      const res = await callWebhook(webhookData({ orderCode: 123 }));

      expect(res).toEqual({ received: true, outcome: 'UNKNOWN_ORDER' });
    });
  });

  describe('đơn quá hạn', () => {
    it('đơn EXPIRED vẫn được cộng khi PayOS báo đã trả — người dùng không mất tiền', async () => {
      putOrder({ status: 'EXPIRED' });

      const res = await callWebhook(webhookData());

      expect(res.outcome).toBe('CREDITED');
      expect(balance).toBe(1_000 + PKG.astrite);
    });
  });

  describe('tạo đơn', () => {
    it('ghi đơn TRƯỚC khi gọi PayOS — không bao giờ có link thanh toán mà thiếu đơn', async () => {
      const callOrder: string[] = [];
      repo.createOrder.mockImplementationOnce(async (o: TopupOrder) => {
        callOrder.push('createOrder');
        orders.set(o.orderCode, o);
        return true;
      });
      payos.createPaymentLink.mockImplementationOnce(async () => {
        callOrder.push('payos');
        return { checkoutUrl: 'https://pay', paymentLinkId: 'l1' } as never;
      });

      await service.createOrder('u1', PKG.packageId);

      expect(callOrder).toEqual(['createOrder', 'payos']);
    });

    it('trả về checkoutUrl + số tiền tra từ gói ở server (không nhận từ client)', async () => {
      const created = await service.createOrder('u1', PKG.packageId);

      expect(created).toMatchObject({
        checkoutUrl: 'https://pay.payos.vn/web/link-1',
        amountVnd: PKG.priceVnd,
        astrite: PKG.astrite,
      });
    });

    it('tạo link PayOS thất bại -> đơn bị đánh dấu CANCELLED và lỗi được ném lên', async () => {
      payos.createPaymentLink.mockRejectedValueOnce(new Error('PayOS 500'));

      await expect(service.createOrder('u1', PKG.packageId)).rejects.toThrow('PayOS 500');
      expect([...orders.values()][0].status).toBe('CANCELLED');
    });

    it('gói đang tắt -> từ chối', async () => {
      repo.findPackageById.mockResolvedValueOnce({ ...PKG, isActive: false });

      await expect(service.createOrder('u1', PKG.packageId)).rejects.toBeInstanceOf(
        BadRequestException,
      );
    });
  });

  describe('xem đơn', () => {
    it('không đọc được đơn của người khác', async () => {
      putOrder({ uid: 'nguoi-khac' });

      await expect(service.getOrderForUser('u1', 111)).rejects.toBeInstanceOf(ForbiddenException);
    });
  });

  describe('endpoint giả lập', () => {
    it('tạo đơn giả lập từ packageId rồi cộng luôn (không gọi PayOS)', async () => {
      const res = await service.simulate('u1', { packageId: PKG.packageId });

      expect(res.outcome).toBe('CREDITED');
      expect(balance).toBe(1_000 + PKG.astrite);
      expect(payos.createPaymentLink).not.toHaveBeenCalled();
      expect(orders.get(res.orderCode)?.isSimulated).toBe(true);
    });

    it('phát lại cùng orderCode -> vẫn chỉ cộng 1 lần', async () => {
      const first = await service.simulate('u1', { packageId: PKG.packageId });
      const again = await service.simulate('u1', { orderCode: first.orderCode });

      expect(again.outcome).toBe('ALREADY_PAID');
      expect(ledger).toHaveLength(1);
    });

    it('môi trường production -> chặn hẳn', async () => {
      nodeEnv = 'production';

      await expect(service.simulate('u1', { packageId: PKG.packageId })).rejects.toBeInstanceOf(
        ForbiddenException,
      );
    });
  });

  describe('thống kê cho admin', () => {
    it('doanh thu chỉ tính đơn PAID và tính trên toàn bộ tập đã lọc', async () => {
      putOrder({ orderCode: 1, status: 'PAID', paidAt: '2026-08-05T01:00:00.000Z' });
      putOrder({ orderCode: 2, status: 'PAID', paidAt: '2026-08-05T02:00:00.000Z' });
      putOrder({ orderCode: 3, status: 'PENDING' });

      // limit 1 -> chi tra ve 1 dong, nhung doanh thu van la ca 2 don da tra
      const { rows, summary } = await service.listAllOrders({ limit: 1 });

      expect(rows).toHaveLength(1);
      expect(summary.paidCount).toBe(2);
      expect(summary.paidRevenueVnd).toBe(PKG.priceVnd * 2);
      expect(summary.pendingCount).toBe(1);
      expect(summary.byDate).toEqual([
        { date: '2026-08-05', revenueVnd: PKG.priceVnd * 2, count: 2 },
      ]);
    });
  });
});
