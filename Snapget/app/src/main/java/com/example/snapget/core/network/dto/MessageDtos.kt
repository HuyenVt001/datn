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
)

/** Nhom chat (memberIds <= 20, nguoi tao tu vao nhom). */
data class ChatGroupDto(
    val groupId: String,
    val groupName: String,
    val memberIds: List<String> = emptyList(),
    val createdBy: String? = null,
    val createdAt: String? = null,
)

/** Body POST /messages/groups — tao nhom (khong can gom nguoi tao). */
data class CreateGroupRequest(
    val groupName: String,
    val memberIds: List<String>,
)
