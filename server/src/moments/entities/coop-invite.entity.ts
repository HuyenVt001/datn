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
  /**
   * TINH LUC TRA VE (khong luu DB — 2026-11-16): moc het han cua trang thai hien tai
   * (PENDING = han loi moi 5 phut, ACCEPTED = han phien chup 10 phut).
   */
  expiresAt?: string;
  /**
   * TINH LUC TRA VE: so giay con lai tinh theo dong ho SERVER. App dem nguoc tu
   * gia tri nay (luc nhan response) thay vi so expiresAt voi gio may — may lech
   * gio van dem dung.
   */
  remainingSeconds?: number;
}

/** Loi moi kem thong tin nguoi moi (tra ve cho app hien danh sach cho). */
export interface CoopInviteView extends CoopInvite {
  inviterName: string;
  inviterAvatar?: string;
}
