package com.example.snapget.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.message.data.MessageRepository
import com.example.snapget.feature.post.data.PostRepository
import com.example.snapget.feature.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel cua feature post — thay the dan phan post trong MainViewModel (god-VM).
 * Du lieu di qua server NestJS: dang bai (upload -> create), feed, seen, reaction.
 */
@HiltViewModel
class PostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    // Gui kem anh vao chat 1-1 khi user chon 1 ban o man dang (tuy chon)
    private val messageRepository: MessageRepository,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    /** Trang thai dang bai — UI hien loading/loi tu day. */
    private val _submitStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val submitStatus: StateFlow<LoadStatus> = _submitStatus.asStateFlow()

    /** Loi gui kem vao chat (dang bai VAN thanh cong) — UI toast roi clear. */
    private val _chatSendError = MutableStateFlow<String?>(null)
    val chatSendError: StateFlow<String?> = _chatSendError.asStateFlow()

    fun clearChatSendError() {
        _chatSendError.value = null
    }

    /** Feed cua minh + ban be. */
    private val _feed = MutableStateFlow<List<MomentDto>>(emptyList())
    val feed: StateFlow<List<MomentDto>> = _feed.asStateFlow()

    private val _feedStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val feedStatus: StateFlow<LoadStatus> = _feedStatus.asStateFlow()

    /** Moment cua tab dang chon ("You" / 1 nguoi ban) — khac voi tab Everyone (feed). */
    private val _userMoments = MutableStateFlow<List<MomentDto>>(emptyList())
    val userMoments: StateFlow<List<MomentDto>> = _userMoments.asStateFlow()

    private val _userMomentsStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val userMomentsStatus: StateFlow<LoadStatus> = _userMomentsStatus.asStateFlow()

    /** Catalog khung — cho picker khi chinh sua + overlay khung tren feed. */
    private val _frames = MutableStateFlow<List<FrameDto>>(emptyList())
    val frames: StateFlow<List<FrameDto>> = _frames.asStateFlow()

    /** Tai catalog khung (goi 1 lan khi can — co cache tai VM). */
    fun loadFrames() {
        if (_frames.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _frames.value = postRepository.getFrames()
            } catch (_: Exception) {
                // Khong co khung thi picker/overlay tu an — khong chan UI
            }
        }
    }

    /**
     * Dang 1 moment: upload file -> tao moment voi URL tra ve.
     * Server tu tang personal streak + bao ban be qua FCM.
     */
    fun submitPhoto(
        file: File,
        isVideo: Boolean = false,
        caption: String? = null,
        frameId: String? = null,
        // Tuy chon: uid cac ban se duoc gui kem anh vao chat 1-1 sau khi dang.
        // Rong = chi dang len feed (mac dinh); "Everyone" o UI = list moi ban be.
        sendToUids: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            _submitStatus.value = LoadStatus.Loading()
            try {
                val uploaded = postRepository.uploadMedia(file, isVideo)
                postRepository.createMoment(
                    mediaUrl = uploaded.url,
                    isVideo = isVideo,
                    caption = caption,
                    frameId = frameId,
                )
                // Gui kem vao chat neu co chon nguoi nhan — loi o buoc nay KHONG lam
                // fail bai dang (moment da len feed roi), chi bao rieng qua chatSendError
                if (sendToUids.isNotEmpty() && isVideo) {
                    // Bubble chat hien chi ho tro PHOTO — video van len feed binh thuong
                    _chatSendError.value = "Videos post to the feed only — sending videos in chat isn't supported yet."
                } else if (sendToUids.isNotEmpty()) {
                    var failed = 0
                    sendToUids.forEach { uid ->
                        try {
                            messageRepository.send(
                                receiverId = uid,
                                content = uploaded.url,
                                messageType = "PHOTO",
                            )
                        } catch (_: Exception) {
                            failed++
                        }
                    }
                    if (failed > 0) {
                        _chatSendError.value =
                            "Posted, but failed to send in chat to $failed friend(s)."
                    }
                }
                _submitStatus.value = LoadStatus.Success()
                loadFeed() // lam moi feed de thay bai vua dang
                widgetRefresher.refreshNow() // widget hien bai vua dang
            } catch (e: Exception) {
                _submitStatus.value = LoadStatus.Error(e.serverMessage("Failed to post."))
            }
        }
    }

    /** Tai feed (trang dau). */
    fun loadFeed() {
        viewModelScope.launch {
            _feedStatus.value = LoadStatus.Loading()
            try {
                _feed.value = postRepository.getFeed().items
                _feedStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _feedStatus.value = LoadStatus.Error(e.serverMessage("Couldn't load feed."))
            }
        }
    }

    /**
     * Tai moment cho tab dang chon: [uid] null = cua minh ("You"),
     * khac null = cua 1 nguoi ban (loi 403 cua server hien truc tiep).
     */
    fun loadUserMoments(uid: String?) {
        viewModelScope.launch {
            _userMomentsStatus.value = LoadStatus.Loading()
            // Xoa bai cua tab TRUOC ngay: khong hien tam bai cua ban cu duoi ten
            // ban moi trong luc cho mang (fix 2026-07-26)
            _userMoments.value = emptyList()
            try {
                _userMoments.value = if (uid == null) {
                    postRepository.getMyMoments()
                } else {
                    postRepository.getUserMoments(uid)
                }
                _userMomentsStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _userMoments.value = emptyList()
                _userMomentsStatus.value = LoadStatus.Error(e.serverMessage("Couldn't load posts."))
            }
        }
    }

    /** Cac moment da mark seen trong phien nay — tranh goi lap khi feed reload. */
    private val seenOnce = mutableSetOf<String>()

    /** Danh dau da xem — goi khi moment hien tren man hinh (fire-and-forget, idempotent). */
    fun markSeen(momentId: String) {
        if (!seenOnce.add(momentId)) return
        viewModelScope.launch {
            try {
                postRepository.markSeen(momentId)
            } catch (_: Exception) {
                seenOnce.remove(momentId) // loi thi cho thu lai lan sau
            }
        }
    }

    /** Tha emoji len moment. */
    fun react(momentId: String, emojiType: String) {
        viewModelScope.launch {
            try {
                postRepository.react(momentId, emojiType)
            } catch (_: Exception) {
                // Reaction that bai thi bo qua, khong chan UI
            }
        }
    }

    /** Reset trang thai dang bai (goi sau khi UI da xu ly xong Success/Error). */
    fun resetSubmitStatus() {
        _submitStatus.value = LoadStatus.Init()
    }

    /** Thong bao 1 lan cho cac hanh dong tren post (xoa/gui tin nhan) — UI toast roi clear. */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    /** Xoa bai cua minh (menu ⋯ tren feed) — xong thi bo khoi state, khong can reload. */
    fun deleteMoment(momentId: String) {
        viewModelScope.launch {
            try {
                postRepository.deleteMoment(momentId)
                _feed.value = _feed.value.filterNot { it.momentId == momentId }
                _userMoments.value = _userMoments.value.filterNot { it.momentId == momentId }
                _actionMessage.value = "Post deleted."
            } catch (e: Exception) {
                _actionMessage.value = e.serverMessage("Failed to delete post.")
            }
        }
    }

    /** Gui tin nhan TEXT toi tac gia bai dang (thanh "Send message..." duoi post). */
    fun sendMessageToAuthor(authorUid: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                messageRepository.send(
                    receiverId = authorUid,
                    content = text.trim(),
                    messageType = "TEXT",
                )
                _actionMessage.value = "Message sent."
            } catch (e: Exception) {
                _actionMessage.value = e.serverMessage("Failed to send message.")
            }
        }
    }
}
