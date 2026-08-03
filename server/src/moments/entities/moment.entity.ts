export type MomentContentType = 'PHOTO' | 'VIDEO';

/**
 * Thuc the Moment (bai dang/khoanh khac) — luu o collection 'posts'.
 * Views va reactions nam trong subcollection cua tung moment.
 */
export interface Moment {
  momentId: string;
  userId: string;
  contentType: MomentContentType;
  mediaUrl: string;
  /** Khung anh ap len moment (frameId) — null neu khong dung. */
  frameId?: string;
  caption?: string;
  /** uid nguoi chup chung (co-op capture) — moment "cua ca 2". */
  coopUserId?: string;
  postTime: string; // ISO string
  /** Id chong dang trung khi client retry sau timeout (app sinh UUID, 2026-08-03). */
  clientRequestId?: string;
}

/** Reaction (emoji bay) trong subcollection posts/{id}/reactions. */
export interface Reaction {
  reactionId: string;
  reactorId: string;
  emojiType: string;
  createdAt: string;
}

/** Trang thai da xem trong subcollection posts/{id}/views. */
export interface MomentView {
  viewerId: string;
  isSeen: boolean;
  seenAt: string;
}
