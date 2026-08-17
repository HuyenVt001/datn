import { AiQuestClass } from '../../common/constants';

/**
 * Log 1 lan AI xac minh anh quest — collection `aiVerifications` (2026-08-15).
 * Ghi MOI lan verify (ke ca truot, ke ca SKIPPED) — nguon so lieu "accuracy thuc
 * te tren production" cho bao cao DATN va de calibrate lai nguong. Ghi best-effort
 * nhu adminLogs: loi log khong anh huong dang bai.
 */
export interface AiVerification {
  uid: string;
  momentId: string;
  /** URL anh DA gui cho AI service (Cloudinary 224x224) — de trang admin hien thumbnail, khong can join posts. */
  mediaUrl?: string;
  /** Ngay quest (YYYY-MM-DD UTC). */
  date: string;
  targetClass: AiQuestClass;
  /** Ket qua verify: khop / khong khop / AI service loi-timeout. */
  outcome: 'MATCHED' | 'NOT_MATCHED' | 'SKIPPED';
  /** Diem sigmoid cua targetClass (khong co khi SKIPPED). */
  score?: number;
  /** Nguong cua targetClass ma model dang dung. */
  threshold?: number;
  /** Du 12 diem — de debug/calibrate (khong co khi SKIPPED). */
  scores?: Record<string, number>;
  modelVersion?: string;
  /** Thoi gian suy luan cua model (ms) — AI service bao ve. */
  latencyMs?: number;
  /** Thoi gian toan bo round-trip server -> AI service (ms). */
  roundTripMs?: number;
  /** Ly do SKIPPED (message loi, khong log URL kem key). */
  error?: string;
  createdAt: string;
}

/** Ban ghi doc tu Firestore (kem doc id) — tra ve cho trang admin. */
export interface AiVerificationRecord extends AiVerification {
  id: string;
}

/** Thong ke verify AI trong 1 ngay (admin dashboard). */
export interface AiVerificationDailyStats {
  date: string;
  total: number;
  matched: number;
  notMatched: number;
  skipped: number;
}
