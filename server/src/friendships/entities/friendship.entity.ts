export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'BLOCKED' | 'DECLINED';

/**
 * Quan he ban be giua 2 user (bang trung gian Friendship trong thiet ke).
 * Moi cap ban chia se 1 friend_streak chung.
 */
export interface Friendship {
  pairId: string; // 2 uid sort roi noi bang '_'
  userIds: string[]; // [uidA, uidB] da sort — de query array-contains
  user1Id: string;
  user2Id: string;
  friendStreak: number;
  lastInteractionAt?: string; // ISO — phuc vu reset streak khi >24h
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
  friendStreak: number;
}
