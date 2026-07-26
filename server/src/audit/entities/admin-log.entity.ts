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
