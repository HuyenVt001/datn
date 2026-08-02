package com.example.snapget.feature.message.data

import com.example.snapget.core.network.api.MessageApi
import com.example.snapget.core.network.api.UploadApi
import com.example.snapget.core.network.dto.AddGroupMembersRequest
import com.example.snapget.core.network.dto.ChatGroupDetailDto
import com.example.snapget.core.network.dto.ChatGroupDto
import com.example.snapget.core.network.dto.ConversationSummaryDto
import com.example.snapget.core.network.dto.CreateGroupRequest
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.core.network.dto.MuteGroupRequest
import com.example.snapget.core.network.dto.ReactMessageRequest
import com.example.snapget.core.network.dto.SendMessageRequest
import com.example.snapget.core.network.dto.UpdateGroupRequest
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.core.network.unwrap
import com.example.snapget.core.network.uploadFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature message — goi API /messages (KHONG cham Firestore).
 * Ho tro 1-1 + nhom; TEXT/EMOJI gui truc tiep, PHOTO/VOICE/STICKER
 * la URL (file upload qua /upload truoc).
 */
@Singleton
class MessageRepository @Inject constructor(
    private val messageApi: MessageApi,
    private val uploadApi: UploadApi,
) {

    /**
     * Gui tin 1-1 (server kiem tra ban be, cong friend streak, push FCM).
     * [attachmentUrl]/[attachmentType]: media dinh kem — dung cho tin reply bai dang
     * (gui kem anh/video cua bai; attachmentType = PHOTO | VIDEO).
     */
    suspend fun send(
        receiverId: String,
        content: String,
        messageType: String = "TEXT",
        attachmentUrl: String? = null,
        attachmentType: String? = null,
        // id tin duoc reply (kieu Messenger) — server validate + snapshot tin goc
        replyToId: String? = null,
    ): MessageDto = messageApi.send(
        SendMessageRequest(
            receiverId = receiverId,
            messageType = messageType,
            content = content,
            attachmentUrl = attachmentUrl,
            attachmentType = attachmentType,
            replyToId = replyToId,
        ),
    ).unwrap()

    /** Tha/go reaction len 1 tin nhan — tra ve tin nhan da cap nhat reactions. */
    suspend fun react(messageId: String, emoji: String): MessageDto = messageApi.react(messageId, ReactMessageRequest(emoji)).unwrap()

    /** Gui tin vao nhom (server kiem tra thanh vien, FCM cho ca nhom). */
    suspend fun sendToGroup(
        groupId: String,
        content: String,
        messageType: String = "TEXT",
        replyToId: String? = null,
    ): MessageDto = messageApi.send(
        SendMessageRequest(
            groupId = groupId,
            messageType = messageType,
            content = content,
            replyToId = replyToId,
        ),
    ).unwrap()

    /** Upload file media (anh/ghi am) len server, tra ve URL lam content tin nhan. */
    suspend fun uploadMedia(file: File, mimeType: String): String = uploadApi.uploadFile(file, mimeType).url

    /** Tao nhom chat (<=20 thanh vien — server enforce). */
    suspend fun createGroup(groupName: String, memberIds: List<String>): ChatGroupDto = messageApi.createGroup(CreateGroupRequest(groupName, memberIds)).unwrap()

    /** Danh sach nhom cua minh. */
    suspend fun listGroups(): List<ChatGroupDto> = messageApi.listGroups().unwrap()

    /** Thread nhom — trang 1 = 50 tin moi nhat, items theo thu tu cu -> moi. */
    suspend fun getGroupThread(groupId: String, page: Int = 1, limit: Int = 50): List<MessageDto> = messageApi.getGroupThread(groupId, page, limit).unwrap().items

    /** Danh sach hoi thoai (tin moi nhat voi tung nguoi). */
    suspend fun getConversations(): List<ConversationSummaryDto> = messageApi.getConversations().unwrap()

    /** Thread 1-1 — trang 1 = 50 tin moi nhat, items theo thu tu cu -> moi. */
    suspend fun getThread(friendUid: String, page: Int = 1, limit: Int = 50): List<MessageDto> = messageApi.getThread(friendUid, page, limit).unwrap().items

    /** Danh dau da xem (fire-and-forget o tang tren). */
    suspend fun markSeen(messageId: String) {
        messageApi.markSeen(messageId).ensureSuccess()
    }

    /** Chi tiet nhom + ho so thanh vien (member-only). */
    suspend fun getGroupDetail(groupId: String): ChatGroupDetailDto = messageApi.getGroupDetail(groupId).unwrap()

    /** Doi ten / anh dai dien nhom (truyen it nhat 1 truong). */
    suspend fun updateGroup(groupId: String, groupName: String? = null, avatar: String? = null): ChatGroupDto = messageApi.updateGroup(groupId, UpdateGroupRequest(groupName, avatar)).unwrap()

    /** Them thanh vien (chi them duoc ban be cua minh — server enforce). */
    suspend fun addGroupMembers(groupId: String, memberIds: List<String>): ChatGroupDto = messageApi.addGroupMembers(groupId, AddGroupMembersRequest(memberIds)).unwrap()

    /** Xoa thanh vien khoi nhom (chi nguoi tao nhom). */
    suspend fun removeGroupMember(groupId: String, memberUid: String): ChatGroupDto = messageApi.removeGroupMember(groupId, memberUid).unwrap()

    /** Roi nhom (nguoi cuoi cung roi -> server xoa nhom). */
    suspend fun leaveGroup(groupId: String) {
        messageApi.leaveGroup(groupId).ensureSuccess()
    }

    /** Bat/tat thong bao nhom cho rieng minh. */
    suspend fun setGroupMuted(groupId: String, muted: Boolean): ChatGroupDto = messageApi.muteGroup(groupId, MuteGroupRequest(muted)).unwrap()
}
