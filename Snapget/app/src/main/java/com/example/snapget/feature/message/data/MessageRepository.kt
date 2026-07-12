package com.example.snapget.feature.message.data

import com.example.snapget.core.network.api.MessageApi
import com.example.snapget.core.network.dto.ConversationSummaryDto
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.core.network.dto.SendMessageRequest
import com.example.snapget.core.network.ensureSuccess
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature message — goi API /messages (KHONG cham Firestore).
 * Chi ho tro 1-1 + TEXT/EMOJI o dot nay (nhom/PHOTO... lam sau).
 */
@Singleton
class MessageRepository @Inject constructor(
    private val messageApi: MessageApi,
) {

    /** Gui tin 1-1 (server kiem tra ban be, cong friend streak, push FCM). */
    suspend fun send(receiverId: String, content: String, messageType: String = "TEXT"): MessageDto = messageApi.send(
        SendMessageRequest(
            receiverId = receiverId,
            messageType = messageType,
            content = content,
        ),
    ).unwrap()

    /** Danh sach hoi thoai (tin moi nhat voi tung nguoi). */
    suspend fun getConversations(): List<ConversationSummaryDto> = messageApi.getConversations().unwrap()

    /** Thread 1-1 — trang 1 = 50 tin moi nhat, items theo thu tu cu -> moi. */
    suspend fun getThread(friendUid: String, page: Int = 1, limit: Int = 50): List<MessageDto> = messageApi.getThread(friendUid, page, limit).unwrap().items

    /** Danh dau da xem (fire-and-forget o tang tren). */
    suspend fun markSeen(messageId: String) {
        messageApi.markSeen(messageId).ensureSuccess()
    }
}
