export type MessageType = 'TEXT' | 'VOICE' | 'EMOJI' | 'STICKER' | 'PHOTO';

/**
 * Thuc the Message. 1-1 -> co receiverId; nhom -> co groupId (mot trong hai null).
 * content = van ban (TEXT/EMOJI) hoac URL file (VOICE/STICKER/PHOTO — upload qua /upload).
 */
export interface Message {
  messageId: string;
  senderId: string;
  receiverId?: string;
  groupId?: string;
  messageType: MessageType;
  content: string;
  sendTime: string; // ISO string
  isSeen: boolean;
}

/** Nhom chat (Chat_Group + Group_Member gop thanh memberIds[]). */
export interface ChatGroup {
  groupId: string;
  groupName: string;
  memberIds: string[]; // <= MAX_GROUP_SIZE
  createdBy: string;
  createdAt: string;
}

/** 1 dong trong danh sach hoi thoai (tin nhan moi nhat voi tung nguoi). */
export interface ConversationSummary {
  counterpartId: string;
  lastMessage: Message;
}
