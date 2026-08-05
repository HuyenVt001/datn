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
  /** So du Astrite (tien te gacha). */
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
  /** So luot BAM NUT quay gacha (quay x10 tinh la 1 luot). */
  gachaRollsToday?: number;
  gachaRollsTotal?: number;
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
  | 'GACHA'
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
  | 'GACHA_ITEM_CREATE'
  | 'GACHA_ITEM_UPDATE'
  | 'GACHA_ITEM_DELETE'
  | 'TOPUP_PACKAGE_CREATE'
  | 'TOPUP_PACKAGE_UPDATE'
  | 'TOPUP_PACKAGE_DELETE';

// ==================== Gacha (2026-08-05) ====================

/** Loai vat pham quay ra. Skin/hieu ung co asset trong APK, khop qua `refId`. */
export type ItemType = 'FRAME' | 'EFFECT' | 'SKIN';

/** Pham chat vat pham. Bac N khong phai vat pham (chi tra Astrite). */
export type ItemRarity = 'R' | 'SR' | 'SSR';

/** Bac quay ra duoc: N (Astrite) + 3 bac vat pham. */
export type RollTier = 'N' | ItemRarity;

/** 1 vat pham trong kho quay (GET /gacha/items/admin). */
export interface GachaItem {
  itemId: string;
  itemName: string;
  itemType: ItemType;
  rarity: ItemRarity;
  imageUrl?: string;
  /** Tro toi vat pham that: FRAME -> frameId · SKIN/EFFECT -> id so trong app. */
  refId: string;
  /** Tat = khong quay ra nua (nguoi da so huu van giu). */
  isActive: boolean;
  sortOrder: number;
  createdAt?: string;
}

/** 1 ket qua le trong 1 luot quay. */
export interface RollResultEntry {
  tier: RollTier;
  /** Bac N: so Astrite nhan duoc. */
  astriteAmount?: number;
  itemId?: string;
  itemName?: string;
  itemType?: ItemType;
  refId?: string;
  imageUrl?: string;
  isDuplicate: boolean;
  refundAstrite: number;
}

/** 1 dong lich su quay cua admin (GET /gacha/history/admin) — 1 doc = 1 luot bam nut. */
export interface AdminRoll {
  rollId: string;
  uid: string;
  /** Ten nguoi quay (server enrich tu uid; khong co doc thi bang chinh uid). */
  fullName: string;
  rollType: 'SINGLE' | 'TEN';
  cost: number;
  results: RollResultEntry[];
  refundTotal: number;
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
