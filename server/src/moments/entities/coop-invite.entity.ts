export type CoopInviteStatus = 'PENDING' | 'COMPLETED' | 'DECLINED' | 'EXPIRED';

/**
 * Loi moi chup chung (Co-op Capture) — collection 'coopInvites'.
 * Nguoi moi chup nua anh truoc; nguoi nhan chap nhan + chup nua con lai
 * -> server ghep 2 anh (sharp, chia doi trai/phai) thanh 1 moment chung.
 */
export interface CoopInvite {
  inviteId: string;
  inviterId: string;
  inviteeId: string;
  /** Nua anh cua nguoi moi (URL Cloudinary tu /upload). */
  inviterMediaUrl: string;
  status: CoopInviteStatus;
  createdAt: string;
  respondedAt?: string;
  /** Moment da tao sau khi ghep (chi khi COMPLETED). */
  momentId?: string;
}

/** Loi moi kem thong tin nguoi moi (tra ve cho app hien danh sach cho). */
export interface CoopInviteView extends CoopInvite {
  inviterName: string;
  inviterAvatar?: string;
}
