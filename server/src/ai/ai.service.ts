import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  AI_GENERATE_TIMEOUT_MS,
  AI_QUEST_CLASSES,
  AI_VERIFY_TIMEOUT_MS,
} from '../common/constants';

/** Response POST /verify cua AI service (QUEST_AI_PLAN muc 7.1). */
export interface AiVerifyResponse {
  /** Quest gop nhom: true neu BAT KY lop nao vuot nguong rieng cua lop do. */
  matched: boolean;
  /** Diem [0,1] cua lop khop (hoac lop co score/threshold cao nhat neu khong khop). */
  score: number;
  /** Nguong cua lop do (artifact di kem model). */
  threshold: number;
  /** Diem cac lop cua nhung model da chay (12 lop y1 va/hoac `person`) — de log & debug. */
  scores: Record<string, number>;
  /** (2026-11-27) Lop da khop — service cu khong tra field nay. */
  matchedClass?: string | null;
  modelVersion: string;
  /** Thoi gian suy luan tren AI service (ms). */
  latencyMs: number;
}

/**
 * Response POST /generate — SERVER PHAI VALIDATE LAI (QuestsService lo).
 * ⚠️ 2026-08-16: user chot BO LLM sinh quest (quest lay tu bo mau 56 cau o
 * ai-quest-templates.ts). Duong nay giu lai lam diem cam san: bat lai bang cach
 * deploy AI service co ENABLE_LLM=1 (hoac doi sang LLM API) — khong phai sua kien truc.
 */
export interface AiGenerateResponse {
  targetClass: string;
  content: string;
}

export interface AiHealthResponse {
  status: string;
  modelVersion?: string;
  llm?: boolean;
}

/**
 * HTTP client goi AI service (FastAPI, code o `ml/ai-service/`) — NOI DUY NHAT trong
 * server biet toi dich vu nay. App khong bao gio goi truc tiep (server = cua ngo).
 *
 * Noi deploy: **Google Cloud Run free tier** (chot 2026-08-16 — Hugging Face da bat
 * Docker Space phai tra phi). Doi cho deploy = doi AI_SERVICE_URL, khong sua code.
 *
 * Fail-safe theo pattern PayOS/Cloudinary: thieu AI_SERVICE_URL / AI_SERVICE_API_KEY
 * -> [enabled] = false, server boot binh thuong, khong verify, quest AI van hien
 * (noi dung tu bo mau) nhung khong bao gio tick. Cung la cong tac thu tu deploy:
 * chi dien env sau khi app Android ban moi da phat hanh (QUEST_AI_PLAN muc 9.3).
 *
 * Khong co repository — service nay KHONG cham Firestore; ghi log verify nam o
 * QuestsRepository (theo khung muc 3 CLAUDE.md).
 */
@Injectable()
export class AiService {
  private readonly logger = new Logger(AiService.name);
  private readonly baseUrl: string;
  private readonly apiKey: string;

  constructor(private readonly config: ConfigService) {
    this.baseUrl = (this.config.get<string>('AI_SERVICE_URL')?.trim() ?? '').replace(/\/+$/, '');
    this.apiKey = this.config.get<string>('AI_SERVICE_API_KEY')?.trim() ?? '';

    if (this.enabled) {
      this.logger.log('AI service configured — AI quest photo verification ON.');
    } else {
      this.logger.warn(
        'AI_SERVICE_URL / AI_SERVICE_API_KEY not configured — AI quest OFF,' +
          'only the 2 fixed quests each day. Other features are unaffected.',
      );
    }
  }

  /** true = du env de goi AI service (co URL + API key). */
  get enabled(): boolean {
    return this.baseUrl.length > 0 && this.apiKey.length > 0;
  }

  /**
   * Xac minh anh co chua BAT KY lop nao trong `targetClasses` khong (quest gop nhom, vd
   * cup | bottle — `aiQuestTargetClasses`). Gui kem `targetClass` = lop dau de AI service doi
   * cu (chi doc targetClass) van chay. Nem loi khi AI service loi/timeout — caller
   * (QuestsService) bat va coi la SKIPPED, KHONG bao gio lam fail dang bai.
   * `imageUrl` nen la URL Cloudinary DA thu nho (xem [toVerifyImageUrl]).
   */
  async verify(imageUrl: string, targetClasses: readonly string[]): Promise<AiVerifyResponse> {
    return this.post<AiVerifyResponse>(
      '/verify',
      { imageUrl, targetClass: targetClasses[0], targetClasses: [...targetClasses] },
      AI_VERIFY_TIMEOUT_MS,
    );
  }

  /**
   * (KHONG DUNG o cau hinh hien tai — xem ghi chu o AiGenerateResponse.)
   * Nho LLM viet quest cho ngay: chon 1 lop ngoai `avoid` + 1 cau tieng Viet.
   * Chay OFFLINE qua cron — timeout dai. Ket qua PHAI duoc validate lai o caller.
   */
  async generate(avoid: readonly string[]): Promise<AiGenerateResponse> {
    return this.post<AiGenerateResponse>(
      '/generate',
      { classes: [...AI_QUEST_CLASSES], avoid: [...avoid] },
      AI_GENERATE_TIMEOUT_MS,
    );
  }

  /** Health cua AI service (modelVersion) — debug + keep-warm. */
  async health(): Promise<AiHealthResponse> {
    return this.request<AiHealthResponse>('GET', '/health', undefined, AI_VERIFY_TIMEOUT_MS);
  }

  /**
   * URL Cloudinary da transform de AI service chi tai ban thu nho (~50-90KB thay vi vai MB):
   * chen `w_640,h_640,c_limit,f_jpg,q_auto/` sau `/upload/`. `c_limit` = thu nho cho canh dai
   * <= 640, GIU TI LE, KHONG CAT — model YOLO y1 (2026-11-23) tu letterbox ve 640.
   * Truoc day dung `w_224,h_224,c_fill`: anh doc 3:4 bi cat vuong mat ~25% tren/duoi + chi con
   * 224px -> vat the nho/lech tam bi mat (1 trong 3 nguyen nhan v0 nhan dien kem, QUEST_AI_PLAN muc 2).
   * Khong phai URL Cloudinary (hoac da co transform) -> tra nguyen — AI service tu resize.
   */
  static toVerifyImageUrl(mediaUrl: string): string {
    const marker = '/image/upload/';
    const idx = mediaUrl.indexOf(marker);
    if (idx < 0) {
      return mediaUrl;
    }
    const head = mediaUrl.slice(0, idx + marker.length);
    const tail = mediaUrl.slice(idx + marker.length);
    // Da co transform (vd "w_..." hoac "c_fill") thi khong chen them
    if (/^[a-z]{1,2}_[^/]+\//.test(tail)) {
      return mediaUrl;
    }
    return `${head}w_640,h_640,c_limit,f_jpg,q_auto/${tail}`;
  }

  private post<T>(path: string, body: unknown, timeoutMs: number): Promise<T> {
    return this.request<T>('POST', path, body, timeoutMs);
  }

  /**
   * fetch cua Node 20 + AbortController timeout. KHONG log URL kem key, KHONG log
   * header — chi log path + status.
   */
  private async request<T>(
    method: 'GET' | 'POST',
    path: string,
    body: unknown,
    timeoutMs: number,
  ): Promise<T> {
    if (!this.enabled) {
      throw new Error('AI service is not configured (AI_SERVICE_URL / AI_SERVICE_API_KEY).');
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(`${this.baseUrl}${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'X-API-Key': this.apiKey,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
      if (!res.ok) {
        throw new Error(`AI service ${path} returned ${res.status}`);
      }
      return (await res.json()) as T;
    } catch (e) {
      const err = e as Error;
      if (err.name === 'AbortError') {
        throw new Error(`AI service ${path} timeout after ${timeoutMs}ms`);
      }
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }
}
