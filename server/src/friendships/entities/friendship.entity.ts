export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'BLOCKED' | 'DECLINED';

/**
 * Quan he ban be giua 2 user (bang trung gian Friendship trong thiet ke).
 * (2026-11-15) Friend streak DA BO theo yeu cau user — chi giu streak ca nhan.
 * Doc cu tren Firestore con field `friendStreak`/`lastInteractionAt`: bo qua khi doc.
 */
export interface Friendship {
  pairId: string; // 2 uid sort roi noi bang '_'
  userIds: string[]; // [uidA, uidB] da sort — de query array-contains
  user1Id: string;
  user2Id: string;
  status: FriendshipStatus;
  /** Nguoi GUI loi moi (bam link cua nguoi kia). Chu link accept/decline. */
  requesterUid?: string;
  createdAt: string;
}

/** 1 loi moi ket ban dang cho minh (chu link) xac nhan. */
export interface FriendRequestSummary {
  uid: string; // uid nguoi gui loi moi
  fullName: string;
  avatar?: string;
  requestedAt: string; // ISO
}

/** Ban be kem thong tin hien thi (tra ve cho danh sach). */
export interface FriendSummary {
  uid: string;
  fullName: string;
  avatar?: string;
}
