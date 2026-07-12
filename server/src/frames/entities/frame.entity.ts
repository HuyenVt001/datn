/** Thuc the Frame (khung anh phan thuong) — admin quan ly catalog. */
export interface Frame {
  frameId: string;
  frameName: string;
  /** URL anh khung (upload qua /upload hoac admin dan URL). */
  imageUrl?: string;
  createdAt: string;
}

/** Frame kem trang thai da mo khoa cua user hien tai. */
export interface FrameWithUnlock extends Frame {
  isUnlocked: boolean;
}
