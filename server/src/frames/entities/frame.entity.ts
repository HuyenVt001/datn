/** Thuc the Frame (khung anh phan thuong) — admin quan ly catalog. */
export interface Frame {
  frameId: string;
  frameName: string;
  /** URL anh khung (upload qua /upload hoac admin dan URL). */
  imageUrl?: string;
  /**
   * Moc streak ca nhan (3/7/14/30) ma khung nay la phan thuong.
   * Khong dat = khung thuong (ung vien thuong ngau nhien khi xong 2/2 quest/ngay).
   * null (chi khi update) = admin XOA moc — repository ghi null de clear field.
   */
  milestone?: number | null;
  createdAt: string;
}

/** Frame kem trang thai da mo khoa cua user hien tai. */
export interface FrameWithUnlock extends Frame {
  isUnlocked: boolean;
}
