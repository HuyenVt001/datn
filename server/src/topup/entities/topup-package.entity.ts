/**
 * Goi nap Astrite (collection `topupPackages`) — GACHA_PLAN.md muc 0.1.
 *
 * Gia va so Astrite CHI doc tu day o phia server. App gui len duy nhat
 * `packageId`; neu nhan `amount`/`astrite` tu client thi ai cung tu khai duoc
 * "toi vua tra 1d, cho toi 5 trieu Astrite".
 */
export interface TopupPackage {
  packageId: string;
  /** Ten hien trong popup nap cua app, vd "600 Astrite". */
  name: string;
  /** So Astrite cong vao vi khi thanh toan thanh cong. */
  astrite: number;
  /** Gia tien VND (so nguyen — PayOS khong nhan so le). */
  priceVnd: number;
  /** Tat = an khoi popup nap cua app (van giu lich su don da tao). */
  isActive: boolean;
  /**
   * Goi dung de KIEM THU (5.201.314 Astrite / 2.000d). Van hien cong khai
   * trong app va KHONG tat (user chot 2026-08-05) — chinh goi nay dung de nap
   * that luc demo. Field nay chi de trang admin danh dau cho de nhan ra.
   */
  isTest: boolean;
  sortOrder: number;
  createdAt: string;
}
