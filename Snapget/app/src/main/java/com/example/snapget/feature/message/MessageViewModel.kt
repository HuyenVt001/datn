package com.example.snapget.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.network.dto.ChatGroupDto
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.friends.data.FriendsRepository
import com.example.snapget.feature.message.data.MessageRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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

    /** Nhom chat cua minh (section "Nhom" tren man Messages). */
    private val _groups = MutableStateFlow<List<ChatGroupDto>>(emptyList())
    val groups: StateFlow<List<ChatGroupDto>> = _groups.asStateFlow()

    /** Dang upload media (anh/voice) — UI hien spinner tren thanh nhap. */
    private val _sendingMedia = MutableStateFlow(false)
    val sendingMedia: StateFlow<Boolean> = _sendingMedia.asStateFlow()

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
                _conversationsStatus.value = LoadStatus.Error(e.serverMessage("Couldn't load conversations."))
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
                _thread.value = mergeThread(messages)
                if (showLoading) _threadStatus.value = LoadStatus.Success()
                markUnseenAsSeen(messages)
            } catch (e: Exception) {
                if (showLoading) {
                    _threadStatus.value = LoadStatus.Error(e.serverMessage("Couldn't load messages."))
                }
                // Polling loi (mat mang thoang qua) -> giu thread cu, khong bao
            }
        }
    }

    /**
     * Gop ket qua poll voi thread hien tai: GIU LAI tin local chua co trong response
     * (tin vua gui xong nhung snapshot cua poll chup TRUOC do — thieu buoc nay thi
     * tin vua gui "bien mat" ~5s roi hien lai, user tuong gui loi va gui lai).
     *
     * CHI giu tin local MOI HON tin moi nhat cua server: thread >50 tin thi tin cu
     * rot khoi trang 1 cua server — khong loc theo sendTime thi tin CU nhat bi
     * append nguoc xuong CUOI thread (sai thu tu + auto-scroll nhay ve tin cu).
     */
    private fun mergeThread(server: List<MessageDto>): List<MessageDto> {
        val serverIds = server.mapTo(mutableSetOf()) { it.messageId }
        val newestServerTime = server.lastOrNull()?.sendTime ?: "" // server tra cu -> moi
        val localOnly = _thread.value.filter {
            it.messageId !in serverIds && it.sendTime > newestServerTime
        }
        return server + localOnly
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
                _sendError.value = e.serverMessage("Failed to send message.")
            }
        }
    }

    /** Tai danh sach nhom cua minh (im lang khi loi — section tu an). */
    fun loadGroups() {
        viewModelScope.launch {
            try {
                _groups.value = messageRepository.listGroups()
            } catch (_: Exception) {
                // Giu danh sach cu
            }
        }
    }

    /** Tao nhom chat roi refresh danh sach (loi hien qua sendError). */
    fun createGroup(groupName: String, memberIds: List<String>) {
        viewModelScope.launch {
            try {
                messageRepository.createGroup(groupName, memberIds)
                loadGroups()
            } catch (e: Exception) {
                _sendError.value = e.serverMessage("Failed to create group.")
            }
        }
    }

    /** Tai/lam moi thread NHOM (dung chung state thread voi chat 1-1). */
    fun refreshGroupThread(groupId: String, showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _threadStatus.value = LoadStatus.Loading()
            try {
                _thread.value = mergeThread(messageRepository.getGroupThread(groupId))
                if (showLoading) _threadStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                if (showLoading) {
                    _threadStatus.value = LoadStatus.Error(e.serverMessage("Couldn't load messages."))
                }
            }
        }
    }

    /** Gui TEXT/EMOJI/STICKER vao nhom. */
    fun sendGroupMessage(groupId: String, content: String, messageType: String = "TEXT") {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val sent = messageRepository.sendToGroup(groupId, trimmed, messageType)
                _thread.value = _thread.value + sent
            } catch (e: Exception) {
                _sendError.value = e.serverMessage("Failed to send message.")
            }
        }
    }

    /**
     * Gui tin media (PHOTO/VOICE): upload file -> gui URL lam content.
     * Dich la 1-1 (receiverId) HOAC nhom (groupId) — truyen dung 1 trong 2.
     */
    fun sendMedia(
        receiverId: String?,
        groupId: String?,
        file: File,
        mimeType: String,
        messageType: String,
    ) {
        if (_sendingMedia.value) return
        viewModelScope.launch {
            _sendingMedia.value = true
            try {
                val url = messageRepository.uploadMedia(file, mimeType)
                val sent = when {
                    groupId != null -> messageRepository.sendToGroup(groupId, url, messageType)
                    receiverId != null -> messageRepository.send(receiverId, url, messageType)
                    else -> return@launch
                }
                _thread.value = _thread.value + sent
            } catch (e: Exception) {
                _sendError.value = e.serverMessage("Failed to send message.")
            } finally {
                _sendingMedia.value = false
            }
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }

    /**
     * Tha/go reaction len 1 tin nhan (long-press bubble). Server toggle: tha lai
     * cung emoji = go. Thanh cong -> thay tin nhan trong thread bang ban co
     * reactions moi (khong doi poll 5s).
     */
    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                val updated = messageRepository.react(messageId, emoji)
                _thread.value = _thread.value.map { message ->
                    if (message.messageId == updated.messageId) updated else message
                }
            } catch (e: Exception) {
                _sendError.value = e.serverMessage("Failed to react.")
            }
        }
    }

    /** Reset thread khi roi man chat (tranh loe tin cu khi mo nguoi khac). */
    fun clearThread() {
        _thread.value = emptyList()
        _threadStatus.value = LoadStatus.Init()
    }

    /** Cac messageId da gui markSeen trong phien — tranh PATCH lap lai moi lan poll 5s. */
    private val seenSent = mutableSetOf<String>()

    /** Danh dau da xem cac tin gui den minh (fire-and-forget, loi bo qua). */
    private fun markUnseenAsSeen(messages: List<MessageDto>) {
        val uid = myUid ?: return
        messages
            .filter { it.receiverId == uid && !it.isSeen && seenSent.add(it.messageId) }
            .forEach { message ->
                viewModelScope.launch {
                    try {
                        messageRepository.markSeen(message.messageId)
                    } catch (_: Exception) {
                        seenSent.remove(message.messageId) // loi thi cho poll sau thu lai
                    }
                }
            }
    }

    /**
     * Chi tai danh sach ban be (ten/avatar) neu CHUA co — cho ChatScreen/GroupChatScreen.
     * Truoc day 2 man nay goi loadConversations() moi lan mo chi de lay ten nguoi chat,
     * keo theo ca GET /messages/conversations (nang) hoan toan thua.
     */
    fun loadFriendsIfNeeded() {
        if (_friendsById.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _friendsById.value = friendsRepository.listFriends().associate { dto ->
                    dto.uid to FriendUi(
                        id = dto.uid,
                        name = dto.fullName ?: "Snapget user",
                        avatar = dto.avatar.orEmpty(),
                        streak = dto.friendStreak ?: 0,
                    )
                }
            } catch (_: Exception) {
                // Ten fallback "Snapget user" — khong chan man chat
            }
        }
    }

    private fun previewOf(message: MessageDto): String = when {
        // Tin reply bai dang (co media dinh kem) -> preview kem icon anh
        message.attachmentUrl != null -> "📷 ${message.content}"
        message.messageType == "PHOTO" -> "📷 Photo"
        message.messageType == "VOICE" -> "🎤 Voice message"
        message.messageType == "STICKER" -> "Sticker"
        else -> message.content
    }
}
