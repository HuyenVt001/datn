export type CoopInviteStatus = 'PENDING' | 'ACCEPTED' | 'COMPLETED' | 'DECLINED' | 'EXPIRED';

/**
 * Loi moi chup chung (redesign 2026-08-02): moi KHONG kem anh; nguoi nhan accept
 * -> ACCEPTED -> ca 2 vao man chup, moi nguoi nop nua anh cua minh; du 2 nua
 * server ghep -> mergedMediaUrl + COMPLETED. Server KHONG tu tao moment nua —
 * moi nguoi cam anh ghep di dang bai theo luong thuong. TTL loi moi: 5 phut.
 */
export interface CoopInvite {
  inviteId: string;
  inviterId: string;
  inviteeId: string;
  /** Nua anh TRAI cua nguoi moi (URL Cloudinary tu /upload) — nop sau khi ACCEPTED. */
  inviterMediaUrl?: string;
  /** Nua anh PHAI cua nguoi nhan. */
  inviteeMediaUrl?: string;
  /** Anh da ghep — co khi COMPLETED; client tai ve roi vao luong edit -> dang bai. */
  mergedMediaUrl?: string;
  status: CoopInviteStatus;
  createdAt: string;
  respondedAt?: string;
  /** Legacy (flow cu: server tu tao moment) — khong con ghi moi. */
  momentId?: string;
}

/** Loi moi kem thong tin nguoi moi (tra ve cho app hien danh sach cho). */
export interface CoopInviteView extends CoopInvite {
  inviterName: string;
  inviterAvatar?: string;
}
