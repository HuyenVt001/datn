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
  createdAt: string;
}

/** Ban be kem thong tin hien thi (tra ve cho danh sach). */
export interface FriendSummary {
  uid: string;
  fullName: string;
  avatar?: string;
  friendStreak: number;
}
