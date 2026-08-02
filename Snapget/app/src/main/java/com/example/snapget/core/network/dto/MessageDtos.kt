package com.example.snapget.core.network.dto

/**
 * DTO cua module /messages tren server NestJS.
 * Tin 1-1 co receiverId, tin nhom co groupId (dung 1 trong 2).
 * content = van ban (TEXT/EMOJI) hoac URL file (VOICE/STICKER/PHOTO).
 */

data class MessageDto(
    val messageId: String,
    val senderId: String,
    val receiverId: String?,
    val groupId: String?,
    val messageType: String,
    val content: String,
    val sendTime: String, // ISO string (UTC)
    val isSeen: Boolean,
    // Media dinh kem (tin reply bai dang gui kem anh/video cua bai)
    val attachmentUrl: String? = null,
    val attachmentType: String? = null, // PHOTO | VIDEO
    // uid -> emoji (moi nguoi 1 reaction, tha lai cung emoji = go)
    val reactions: Map<String, String>? = null,
    // Tin nay REPLY 1 tin khac trong cung hoi thoai (kieu Messenger) —
    // server snapshot san type/content/sender cua tin goc de ve khoi trich dan
    val replyToId: String? = null,
    val replyToType: String? = null,
    val replyToContent: String? = null,
    val replyToSenderId: String? = null,
)

/** 1 dong trong danh sach hoi thoai: tin moi nhat voi tung nguoi. */
data class ConversationSummaryDto(
    val counterpartId: String,
    val lastMessage: MessageDto,
)

/** Body POST /messages — gui 1-1 (receiverId) hoac nhom (groupId). */
data class SendMessageRequest(
    val receiverId: String? = null,
    val groupId: String? = null,
    val messageType: String = "TEXT",
    val content: String,
    val attachmentUrl: String? = null,
    val attachmentType: String? = null,
    // id tin duoc reply (server validate cung hoi thoai + tu snapshot tin goc)
    val replyToId: String? = null,
)

/** Body POST /messages/{id}/reactions — tha/go reaction (toggle cung emoji). */
data class ReactMessageRequest(
    val emoji: String,
)

/** Nhom chat (memberIds <= 20, nguoi tao tu vao nhom). */
data class ChatGroupDto(
    val groupId: String,
    val groupName: String,
    // Anh dai dien nhom (URL Cloudinary) — null = chua dat, app hien icon nhom
    val avatar: String? = null,
    val memberIds: List<String> = emptyList(),
    val createdBy: String? = null,
    val createdAt: String? = null,
    // uid da tat thong bao nhom (chua uid cua minh -> switch Mute dang bat)
    val mutedBy: List<String> = emptyList(),
)

/** Body POST /messages/groups — tao nhom (khong can gom nguoi tao). */
data class CreateGroupRequest(
    val groupName: String,
    val memberIds: List<String>,
)

/** Ho so cong khai 1 thanh vien nhom (tu GET /messages/groups/{id}/detail). */
data class GroupMemberDto(
    val uid: String,
    val fullName: String? = null,
    val avatar: String? = null,
)

/** Chi tiet nhom = ChatGroup + danh sach thanh vien da resolve ten/avatar. */
data class ChatGroupDetailDto(
    val groupId: String,
    val groupName: String,
    val avatar: String? = null,
    val memberIds: List<String> = emptyList(),
    val createdBy: String? = null,
    val createdAt: String? = null,
    val mutedBy: List<String> = emptyList(),
    val members: List<GroupMemberDto> = emptyList(),
)

/** Body PATCH /messages/groups/{id} — doi ten/anh dai dien nhom (it nhat 1 truong). */
data class UpdateGroupRequest(
    val groupName: String? = null,
    val avatar: String? = null,
)

/** Body POST /messages/groups/{id}/members — them thanh vien (phai la ban be cua minh). */
data class AddGroupMembersRequest(
    val memberIds: List<String>,
)

/** Body PATCH /messages/groups/{id}/mute — bat/tat thong bao nhom cho rieng minh. */
data class MuteGroupRequest(
    val muted: Boolean,
)
