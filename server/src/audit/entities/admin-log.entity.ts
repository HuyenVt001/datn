/** Cac hanh dong admin duoc ghi nhat ky. */
export const ADMIN_ACTIONS = [
  'USER_DISABLE',
  'USER_ENABLE',
  'GRANT_ADMIN',
  'REVOKE_ADMIN',
  'FRAME_CREATE',
  'FRAME_UPDATE',
  'FRAME_DELETE',
  'FRAME_GRANT',
  'MOMENT_DELETE',
  // (2026-08-05 — G3) quan ly kho vat pham gacha
  'GACHA_ITEM_CREATE',
  'GACHA_ITEM_UPDATE',
  'GACHA_ITEM_DELETE',
  // (2026-08-06) kho thuong: admin tang vat pham thang vao tai khoan
  'GACHA_ITEM_GRANT',
  // (2026-08-05 — G6) quan ly goi nap PayOS. Doi gia/so Astrite la hanh dong
  // lien quan TIEN THAT nen bat buoc co dau vet ai sua, luc nao.
  'TOPUP_PACKAGE_CREATE',
  'TOPUP_PACKAGE_UPDATE',
  'TOPUP_PACKAGE_DELETE',
] as const;

export type AdminAction = (typeof ADMIN_ACTIONS)[number];

/** 1 dong nhat ky hanh dong admin (collection `adminLogs`). */
export interface AdminLog {
  logId: string;
  /** Ai lam (uid + email cua admin thao tac). */
  actorUid: string;
  actorEmail?: string;
  action: AdminAction;
  /** Doi tuong bi tac dong (uid user / frameId / momentId). */
  targetId?: string;
  /** Nhan de doc (email user / ten khung / caption bai...). */
  targetLabel?: string;
  createdAt: string;
}
