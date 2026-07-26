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
  createdAt: string;
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

/** Khung anh trong catalog (GET /frames/admin). */
export interface Frame {
  frameId: string;
  frameName: string;
  imageUrl?: string;
  /** Moc streak (3/7/14/30) neu khung la phan thuong moc; khong co = khung thuong quest. */
  milestone?: number;
  createdAt?: string;
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
  email?: string;
}
