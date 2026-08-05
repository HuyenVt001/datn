/**
 * Don nap Astrite (collection `topupOrders`) — GACHA_PLAN.md muc 2.2 + 4.
 *
 * ⚠️ **Doc id = `orderCode`** (so nguyen PayOS bat buoc). Day chinh la co che
 * chong cong tien 2 lan: webhook goi lai bao nhieu lan cung tra ve dung 1 doc,
 * va transaction chi cong Astrite khi doc do con `status === 'PENDING'`.
 */
export const TOPUP_ORDER_STATUSES = [
  'PENDING', // vua tao link, chua tra tien
  'PAID', // webhook da xac nhan + da cong Astrite
  'CANCELLED', // nguoi dung bam huy o trang PayOS / tao link that bai
  'EXPIRED', // qua han TOPUP_ORDER_TTL_MINUTES ma chua tra
] as const;

export type TopupOrderStatus = (typeof TOPUP_ORDER_STATUSES)[number];

export interface TopupOrder {
  /** So nguyen; cung la doc id (dang chuoi trong Firestore). */
  orderCode: number;
  uid: string;
  packageId: string;
  /** Chup lai ten goi luc tao don — admin doi ten goi sau khong lam lech lich su. */
  packageName: string;
  /** So Astrite se cong. Chup tu goi luc tao don, khong doc lai luc webhook ve. */
  astrite: number;
  amountVnd: number;
  status: TopupOrderStatus;
  /** Id link thanh toan ben PayOS (de doi soat tren dashboard PayOS). */
  payosPaymentLinkId?: string;
  checkoutUrl?: string;
  /** Ma giao dich ngan hang PayOS gui ve trong webhook. */
  payosReference?: string;
  /** Don sinh boi `POST /topup/simulate` (chi co o moi truong dev). */
  isSimulated?: boolean;
  createdAt: string;
  paidAt?: string;
}

/** 1 dong trong danh sach don cua trang admin (them ten nguoi nap). */
export interface AdminTopupRow extends TopupOrder {
  fullName: string;
}

/** Thong ke doanh thu kem theo danh sach don cua admin. */
export interface TopupRevenueSummary {
  /** Tong VND cua cac don PAID trong tap dang xem. */
  paidRevenueVnd: number;
  paidCount: number;
  pendingCount: number;
  /** Tong Astrite da phat ra qua nap (doi chieu voi so cai). */
  paidAstrite: number;
  /** Doanh thu theo ngay UTC, moi nhat truoc — cho bieu do/bang o trang admin. */
  byDate: { date: string; revenueVnd: number; count: number }[];
}
