import { Injectable, Logger, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PayOS, type CreatePaymentLinkResponse, type Webhook, type WebhookData } from '@payos/node';

/** Tham so tao 1 link thanh toan (chi nhung gi Snapget dung toi). */
export interface CreateLinkParams {
  orderCode: number;
  amountVnd: number;
  /** ⚠️ Toi da 25 ky tu (PAYOS_DESCRIPTION_MAX) — TopupService lo cat. */
  description: string;
  /** Moc het han cua link, dang unix SECONDS (PayOS quy dinh). */
  expiresAtUnixSeconds: number;
}

/**
 * NOI DUY NHAT goi SDK `@payos/node` (v2). Moi cho khac trong server chi biet
 * toi service nay.
 *
 * Vi sao boc lai:
 *  - **Server phai boot duoc khi chua co khoa.** Ca 3 khoa deu optional trong
 *    `env.validation`; thieu khoa thi [isConfigured] = false, chi rieng luong
 *    nap tat (503) con toan bo phan con lai cua app chay binh thuong. Nho vay
 *    e2e va CI khong can khoa that.
 *  - **Khoa khong bao gio ro ra log.** Cac ham duoi day chi log `orderCode`.
 *    Tuyet doi khong log `error.request`, khong log object config.
 *  - Doi phien ban SDK (v1 -> v2 doi han API) chi phai sua 1 file.
 */
@Injectable()
export class PayosService {
  private readonly logger = new Logger(PayosService.name);
  private readonly client: PayOS | null;
  private readonly returnUrl: string;
  private readonly cancelUrl: string;

  constructor(private readonly config: ConfigService) {
    const clientId = this.config.get<string>('PAYOS_CLIENT_ID')?.trim() ?? '';
    const apiKey = this.config.get<string>('PAYOS_API_KEY')?.trim() ?? '';
    const checksumKey = this.config.get<string>('PAYOS_CHECKSUM_KEY')?.trim() ?? '';
    this.returnUrl = this.config.get<string>('PAYOS_RETURN_URL')?.trim() ?? '';
    this.cancelUrl = this.config.get<string>('PAYOS_CANCEL_URL')?.trim() ?? '';

    if (clientId && apiKey && checksumKey) {
      this.client = new PayOS({ clientId, apiKey, checksumKey });
      this.logger.log('PayOS ready (REAL MONEY mode).');
      if (!this.returnUrl || !this.cancelUrl) {
        // Bat som o day thay vi de PayOS tra 400 kho hieu luc nguoi dung bam nap.
        this.logger.error(
          'All 3 PayOS keys are set but PAYOS_RETURN_URL / PAYOS_CANCEL_URL are MISSING —' +
            'creating payment links will fail. Remember the /api prefix in these 2 URLs.',
        );
      }
    } else {
      this.client = null;
      this.logger.warn(
        'PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY not configured —' +
          'top-up flow OFF. Other features are unaffected.',
      );
    }
  }

  /** true = du 3 khoa, tao duoc link thanh toan that. */
  get isConfigured(): boolean {
    return this.client !== null;
  }

  /** true = co khoa checksum -> ky duoc payload gia lap cho `/topup/simulate`. */
  get canSign(): boolean {
    return this.client !== null;
  }

  /**
   * Tao link thanh toan. Nem 503 neu chua cau hinh khoa — thong bao tieng Viet
   * vi chuoi nay hien thang len app.
   *
   * `expiredAt` de PayOS TU tu choi thanh toan qua han, khop voi
   * `TOPUP_ORDER_TTL_MINUTES` ma server dung de danh dau don EXPIRED — neu chi
   * server danh dau con link van song thi don "het han" van tra tien duoc.
   */
  async createPaymentLink(params: CreateLinkParams): Promise<CreatePaymentLinkResponse> {
    const client = this.requireClient();
    if (!this.returnUrl || !this.cancelUrl) {
      throw new ServiceUnavailableException(
        'The server is missing the PayOS return URL configuration. Please try again later.',
      );
    }
    return client.paymentRequests.create({
      orderCode: params.orderCode,
      amount: params.amountVnd,
      description: params.description,
      returnUrl: this.returnUrl,
      cancelUrl: this.cancelUrl,
      expiredAt: params.expiresAtUnixSeconds,
    });
  }

  /**
   * Kiem tra chu ky webhook. SDK nem loi neu chu ky sai — service goi phai bat
   * va tra 401.
   *
   * ⚠️ SDK CHI kiem tra toan ven du lieu, KHONG kiem tra giao dich thanh cong.
   * Viec doc `data.code === '00'` la trach nhiem cua TopupService.
   */
  async verifyWebhook(body: Webhook): Promise<WebhookData> {
    return this.requireClient().webhooks.verify(body);
  }

  /**
   * Ky mot payload webhook bang chinh khoa checksum — CHI dung cho
   * `/topup/simulate` o moi truong dev, de duong test di qua dung ham verify
   * that thay vi di tat.
   */
  async signWebhookData(data: WebhookData): Promise<string> {
    const client = this.requireClient();
    const signature = await client.crypto.createSignatureFromObj(data, client.checksumKey);
    if (!signature) {
      throw new ServiceUnavailableException('Failed to sign the simulated webhook data.');
    }
    return signature;
  }

  /**
   * Dang ky URL webhook voi PayOS (PayOS se goi thu URL nay ngay luc dang ky).
   * Thuong lam bang tay tren trang my.payos.vn; ham nay de danh cho script.
   */
  async confirmWebhook(webhookUrl: string): Promise<void> {
    await this.requireClient().webhooks.confirm(webhookUrl);
    this.logger.log('Webhook registered with PayOS.');
  }

  private requireClient(): PayOS {
    if (!this.client) {
      throw new ServiceUnavailableException(
        'Astrite top-up is not configured on the server. Please try again later.',
      );
    }
    return this.client;
  }
}
