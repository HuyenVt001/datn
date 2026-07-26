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
  | 'QUEST_RANDOM'
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
  | 'MOMENT_DELETE';

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
