package com.example.snapget.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.feature.friends.data.FriendsRepository
import com.example.snapget.feature.message.data.MessageRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 1 dong hoi thoai da resolve ten/avatar (tu danh sach ban be). */
data class ConversationUi(
    val counterpartId: String,
    val name: String,
    val avatar: String,
    val preview: String,
    val sendTime: String,
    val unread: Boolean,
)

/**
 * ViewModel cua feature message — thay phan message trong MainViewModel (god-VM).
 * Nguon du lieu: API /messages. Tin moi lay bang POLLING (ChatScreen goi
 * refreshThread ~5s/lan) vi server la REST thuan.
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val friendsRepository: FriendsRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    val myUid: String? get() = auth.currentUser?.uid

    /** Ban be (uid -> ten/avatar) de resolve nguoi trong hoi thoai. */
    private val _friendsById = MutableStateFlow<Map<String, FriendUi>>(emptyMap())
    val friendsById: StateFlow<Map<String, FriendUi>> = _friendsById.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationUi>>(emptyList())
    val conversations: StateFlow<List<ConversationUi>> = _conversations.asStateFlow()

    private val _conversationsStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val conversationsStatus: StateFlow<LoadStatus> = _conversationsStatus.asStateFlow()

    /** Thread 1-1 dang mo (cu -> moi). */
    private val _thread = MutableStateFlow<List<MessageDto>>(emptyList())
    val thread: StateFlow<List<MessageDto>> = _thread.asStateFlow()

    private val _threadStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val threadStatus: StateFlow<LoadStatus> = _threadStatus.asStateFlow()

    /** Loi gui tin gan nhat (UI hien Toast roi goi clearSendError). */
    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    /** Tai danh sach hoi thoai + ban be (goi khi mo man Messages). */
    fun loadConversations() {
        viewModelScope.launch {
            _conversationsStatus.value = LoadStatus.Loading()
            try {
                // Ban be truoc de co ten/avatar (nhan tin chi voi ban be)
                val friends = friendsRepository.listFriends().associate { dto ->
                    dto.uid to FriendUi(
                        id = dto.uid,
                        name = dto.fullName ?: "Snapget user",
                        avatar = dto.avatar.orEmpty(),
                        streak = dto.friendStreak ?: 0,
                    )
                }
                _friendsById.value = friends

                val uid = myUid
                _conversations.value = messageRepository.getConversations().map { convo ->
                    val friend = friends[convo.counterpartId]
                    val last = convo.lastMessage
                    ConversationUi(
                        counterpartId = convo.counterpartId,
                        name = friend?.name ?: "Snapget user",
                        avatar = friend?.avatar.orEmpty(),
                        preview = previewOf(last),
                        sendTime = last.sendTime,
                        // Chua doc = tin cuoi gui DEN minh va chua seen
                        unread = last.receiverId == uid && !last.isSeen,
                    )
                }
                _conversationsStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _conversationsStatus.value = LoadStatus.Error(e.message ?: "Khong tai duoc hoi thoai.")
            }
        }
    }

    /**
     * Tai/lam moi thread voi 1 nguoi. [showLoading] = true chi o lan dau
     * (cac lan polling sau im lang de UI khong nhay spinner).
     */
    fun refreshThread(friendUid: String, showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _threadStatus.value = LoadStatus.Loading()
            try {
                val messages = messageRepository.getThread(friendUid)
                _thread.value = messages
                if (showLoading) _threadStatus.value = LoadStatus.Success()
                markUnseenAsSeen(messages)
            } catch (e: Exception) {
                if (showLoading) {
                    _threadStatus.value = LoadStatus.Error(e.message ?: "Khong tai duoc tin nhan.")
                }
                // Polling loi (mat mang thoang qua) -> giu thread cu, khong bao
            }
        }
    }

    /** Gui TEXT/EMOJI; thanh cong -> append ngay vao thread (polling se dong bo lai). */
    fun sendMessage(friendUid: String, content: String, messageType: String = "TEXT") {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val sent = messageRepository.send(friendUid, trimmed, messageType)
                _thread.value = _thread.value + sent
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Gui tin that bai."
            }
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }

    /** Reset thread khi roi man chat (tranh loe tin cu khi mo nguoi khac). */
    fun clearThread() {
        _thread.value = emptyList()
        _threadStatus.value = LoadStatus.Init()
    }

    /** Danh dau da xem cac tin gui den minh (fire-and-forget, loi bo qua). */
    private fun markUnseenAsSeen(messages: List<MessageDto>) {
        val uid = myUid ?: return
        messages
            .filter { it.receiverId == uid && !it.isSeen }
            .forEach { message ->
                viewModelScope.launch {
                    try {
                        messageRepository.markSeen(message.messageId)
                    } catch (_: Exception) {
                        // Khong anh huong UX
                    }
                }
            }
    }

    private fun previewOf(message: MessageDto): String = when (message.messageType) {
        "TEXT", "EMOJI" -> message.content
        "PHOTO" -> "📷 Ảnh"
        "VOICE" -> "🎤 Tin nhắn thoại"
        "STICKER" -> "Sticker"
        else -> message.content
    }
}
