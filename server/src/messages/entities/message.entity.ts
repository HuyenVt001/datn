export type MessageType = 'TEXT' | 'VOICE' | 'EMOJI' | 'STICKER' | 'PHOTO';

/** Loai media dinh kem theo tin nhan (reply bai dang gui kem anh/video cua bai). */
export type AttachmentType = 'PHOTO' | 'VIDEO';

/**
 * Thuc the Message. 1-1 -> co receiverId; nhom -> co groupId (mot trong hai null).
 * content = van ban (TEXT/EMOJI) hoac URL file (VOICE/STICKER/PHOTO — upload qua /upload).
 * attachmentUrl/attachmentType: media dinh kem (tin reply bai dang mang anh/video cua bai).
 * reactions: map uid -> emoji (moi nguoi toi da 1 reaction, bam lai emoji cu = bo).
 * replyToId + replyTo*: tin nay REPLY 1 tin khac trong cung hoi thoai (kieu Messenger) —
 * server snapshot type/content/senderId cua tin goc de app ve khoi trich dan
 * khong can lookup (tin goc co the nam ngoai trang thread dang tai).
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
  attachmentUrl?: string;
  attachmentType?: AttachmentType;
  reactions?: Record<string, string>;
  replyToId?: string;
  replyToType?: MessageType;
  replyToContent?: string;
  replyToSenderId?: string;
}

/**
 * Nhom chat (Chat_Group + Group_Member gop thanh memberIds[]).
 * avatar: URL Cloudinary (doi qua PATCH /messages/groups/:id, upload truoc qua /upload).
 * mutedBy: uid da tat thong bao nhom — sendToGroup bo qua khi gui FCM.
 */
export interface ChatGroup {
  groupId: string;
  groupName: string;
  avatar?: string;
  memberIds: string[]; // <= MAX_GROUP_SIZE
  createdBy: string;
  createdAt: string;
  mutedBy?: string[];
}

/** Thong tin cong khai 1 thanh vien nhom (phuc vu man cai dat nhom o app). */
export interface GroupMemberSummary {
  uid: string;
  fullName: string;
  avatar?: string;
}

/** Chi tiet nhom = nhom + ho so cong khai tung thanh vien. */
export interface ChatGroupDetail extends ChatGroup {
  members: GroupMemberSummary[];
}

/** 1 dong trong danh sach hoi thoai (tin nhan moi nhat voi tung nguoi). */
export interface ConversationSummary {
  counterpartId: string;
  lastMessage: Message;
}
