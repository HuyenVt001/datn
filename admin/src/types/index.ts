/** Envelope chuan cua server NestJS — moi response deu boc dang nay. */
export interface ApiEnvelope<T> {
  success: boolean;
  statusCode: number;
  message: string;
  data: T;
}

/** Ket qua phan trang chuan (nam trong `data`). */
export interface Paginated<T> {
  items: T[];
  page: number;
  limit: number;
  total: number;
}

/** 1 dong user trong trang Quan ly nguoi dung (GET /admin/users). */
export interface AdminUser {
  uid: string;
  email?: string;
  fullName: string;
  disabled: boolean;
  /** true = co quyen admin (custom claim) — truy cap duoc trang quan tri nay. */
  admin: boolean;
  createdAt: string;
  lastSignInAt?: string;
  /** So du Astrite (tien te mua vat pham o Shop). */
  astrite: number;
}

/** Thong ke tong quan (GET /admin/stats). */
export interface AdminStats {
  users: number;
  moments: number;
  momentsToday: number;
  messages: number;
  friendships: number;
  chatGroups: number;
  /** So luot hoan thanh quest hom nay. */
  questCompletionsToday?: number;
  /** (2026-11-15) So luot mua vat pham o Shop (thay so luot quay gacha). */
  shopPurchasesToday?: number;
  shopPurchasesTotal?: number;
  /** (2026-08-16) So lan AI xac minh anh quest hom nay / so lan KHOP (quest AI tick). */
  aiVerificationsToday?: number;
  aiMatchedToday?: number;
}

/** 1 diem du lieu bieu do thong ke theo ngay (GET /admin/stats/daily). */
export interface DailyStat {
  /** Ngay UTC dang YYYY-MM-DD. */
  date: string;
  moments: number;
  newUsers: number;
}

/** Dieu kien mo khoa khung (khop UNLOCK_TYPES cua server). */
export type UnlockType =
  | 'SHOP'
  | 'STREAK_MILESTONE'
  | 'POST_COUNT'
  | 'FRIEND_COUNT'
  | 'COOP_FIRST'
  | 'DEFAULT';

/** Khung anh trong catalog (GET /frames/admin). */
export interface Frame {
  frameId: string;
  frameName: string;
  imageUrl?: string;
  /** Dieu kien mo khoa cua khung. */
  unlockType: UnlockType;
  /** Nguong N (moc streak / so bai / so ban) — null voi loai khong can nguong. */
  unlockValue?: number | null;
  /** Legacy (= unlockValue khi STREAK_MILESTONE) — app Android van doc field nay. */
  milestone?: number | null;
  createdAt?: string;
}

/** 1 user dang so huu khung (GET /frames/:id/owners). */
export interface FrameOwner {
  uid: string;
  email?: string;
  fullName: string;
  avatar?: string;
}

/** Ket qua GET /frames/:id/owners. */
export interface FrameOwnersResult {
  frame: Frame;
  owners: FrameOwner[];
}

/** 1 bai dang trong trang kiem duyet (GET /admin/moments). */
export interface AdminMoment {
  momentId: string;
  userId: string;
  authorName: string;
  contentType: string;
  mediaUrl: string;
  caption?: string;
  coopUserId?: string;
  postTime: string;
}

/** Hanh dong admin duoc ghi nhat ky (khop ADMIN_ACTIONS server). */
export type AdminAction =
  | 'USER_DISABLE'
  | 'USER_ENABLE'
  | 'GRANT_ADMIN'
  | 'REVOKE_ADMIN'
  | 'FRAME_CREATE'
  | 'FRAME_UPDATE'
  | 'FRAME_DELETE'
  | 'FRAME_GRANT'
  | 'MOMENT_DELETE'
  | 'SHOP_ITEM_CREATE'
  | 'SHOP_ITEM_UPDATE'
  | 'SHOP_ITEM_DELETE'
  | 'SHOP_ITEM_GRANT'
  // Log cu truoc 2026-11-15 (gacha da bo) — van con trong adminLogs, giu de hien dung nhan
  | 'GACHA_ITEM_CREATE'
  | 'GACHA_ITEM_UPDATE'
  | 'GACHA_ITEM_DELETE'
  | 'GACHA_ITEM_GRANT'
  | 'TOPUP_PACKAGE_CREATE'
  | 'TOPUP_PACKAGE_UPDATE'
  | 'TOPUP_PACKAGE_DELETE';

// ==================== Shop (2026-11-15 — thay gacha) ====================

/** Loai mat hang. Skin/hieu ung co asset trong APK, khop qua `refId`. */
export type ShopItemType = 'FRAME' | 'SKIN' | 'EFFECT';

/** 1 mat hang trong Shop (GET /shop/items/admin). */
export interface ShopItem {
  itemId: string;
  itemName: string;
  itemType: ShopItemType;
  /** Tro toi vat pham that: FRAME -> frameId · SKIN/EFFECT -> id so trong app (>= 1). */
  refId: string;
  /** Gia ban (Astrite). */
  price: number;
  imageUrl?: string;
  /** Tat = an khoi Shop (nguoi da mua van giu). */
  isActive: boolean;
  sortOrder: number;
  createdAt?: string;
}

/** 1 user dang so huu vat pham (GET /shop/items/:id/owners). */
export interface ShopItemOwner {
  uid: string;
  email?: string;
  fullName: string;
  avatar?: string;
}

/** Ket qua GET /shop/items/:id/owners. */
export interface ShopItemOwnersResult {
  item: ShopItem;
  owners: ShopItemOwner[];
}

/** 1 dong lich su mua (GET /shop/history/admin) — snapshot ten/gia luc mua. */
export interface AdminPurchase {
  purchaseId: string;
  uid: string;
  /** Ten nguoi mua (server enrich tu uid; khong co doc thi bang chinh uid). */
  fullName: string;
  itemId: string;
  itemName: string;
  itemType: ShopItemType;
  refId: string;
  price: number;
  balanceAfter: number;
  createdAt: string;
}

// ==================== Nap Astrite qua PayOS (2026-08-05) ====================

/** 1 goi nap (GET /topup/packages/admin). */
export interface TopupPackage {
  packageId: string;
  name: string;
  astrite: number;
  priceVnd: number;
  /** Tat = an khoi popup nap cua app (lich su don cu van giu). */
  isActive: boolean;
  /** Goi dung de kiem thu — chi de admin de nhan ra, khong doi hanh vi gi. */
  isTest: boolean;
  sortOrder: number;
  createdAt?: string;
}

/** Trang thai don nap (khop TOPUP_ORDER_STATUSES cua server). */
export type TopupOrderStatus = 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED';

/** 1 dong lich su nap cua admin (GET /topup/history/admin). */
export interface AdminTopupOrder {
  orderCode: number;
  uid: string;
  /** Ten nguoi nap (server enrich tu uid). */
  fullName: string;
  packageId: string;
  packageName: string;
  astrite: number;
  amountVnd: number;
  status: TopupOrderStatus;
  payosPaymentLinkId?: string;
  checkoutUrl?: string;
  /** Ma giao dich ngan hang — dung de doi soat voi dashboard PayOS. */
  payosReference?: string;
  /** Don sinh boi /topup/simulate (chi co o moi truong dev). */
  isSimulated?: boolean;
  createdAt: string;
  paidAt?: string;
}

/** Thong ke doanh thu di kem danh sach don. */
export interface TopupRevenueSummary {
  paidRevenueVnd: number;
  paidCount: number;
  pendingCount: number;
  paidAstrite: number;
  byDate: { date: string; revenueVnd: number; count: number }[];
}

/** Ket qua GET /topup/history/admin. */
export interface AdminTopupResult {
  rows: AdminTopupOrder[];
  summary: TopupRevenueSummary;
}

/** 1 dong nhat ky admin (GET /admin/logs). */
export interface AdminLog {
  logId: string;
  actorUid: string;
  actorEmail?: string;
  action: AdminAction;
  targetId?: string;
  targetLabel?: string;
  createdAt: string;
}

/** Ket qua 1 lan AI xac minh anh quest (GET /admin/ai-verifications) — 2026-08-16. */
export type AiVerificationOutcome = 'MATCHED' | 'NOT_MATCHED' | 'SKIPPED';

export interface AiVerification {
  id: string;
  uid: string;
  momentId: string;
  /** URL anh DA thu nho gui cho AI service (canh dai <= 640, giu ti le; log cu: 224x224) — dung lam thumbnail. */
  mediaUrl?: string;
  /** Ngay quest (YYYY-MM-DD UTC). */
  date: string;
  /** Key quest (1 trong 7 quest; log truoc 2026-11-27 la 1 lop don trong 9 lop cu). */
  targetClass: string;
  /** (2026-11-27) Cac lop duoc chap nhan cua quest gop nhom, vd ['cup','bottle']. */
  targetClasses?: string[];
  /** (2026-11-27) Lop da khop (chi khi MATCHED). */
  matchedClass?: string;
  outcome: AiVerificationOutcome;
  /** Diem [0,1] cua lop khop / lop gan nguong nhat trong quest (khong co khi SKIPPED). */
  score?: number;
  /** Nguong cua lop do ma model dang dung. */
  threshold?: number;
  /** Diem cac lop cua model da chay (12 lop y1 va/hoac person) — de debug/calibrate. */
  scores?: Record<string, number>;
  modelVersion?: string;
  /** ms suy luan tren AI service. */
  latencyMs?: number;
  /** ms round-trip server -> AI service. */
  roundTripMs?: number;
  /** Ly do SKIPPED. */
  error?: string;
  createdAt: string;
}

/** Ket qua upload media (POST /upload/admin). */
export interface UploadResult {
  url: string;
  publicId: string;
  resourceType: 'image' | 'video';
}

/** Ket qua dang nhap admin (POST /auth/admin/login). */
export interface AdminLoginResult {
  accessToken: string;
  uid: string;
  email?: string;
}
