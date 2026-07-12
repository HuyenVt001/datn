package com.example.snapget.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.feature.post.data.PostRepository
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
) : ViewModel() {

    /** Trang thai dang bai — UI hien loading/loi tu day. */
    private val _submitStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val submitStatus: StateFlow<LoadStatus> = _submitStatus.asStateFlow()

    /** Feed cua minh + ban be. */
    private val _feed = MutableStateFlow<List<MomentDto>>(emptyList())
    val feed: StateFlow<List<MomentDto>> = _feed.asStateFlow()

    private val _feedStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val feedStatus: StateFlow<LoadStatus> = _feedStatus.asStateFlow()

    /**
     * Dang 1 moment: upload file -> tao moment voi URL tra ve.
     * Server tu tang personal streak + bao ban be qua FCM.
     */
    fun submitPhoto(
        file: File,
        isVideo: Boolean = false,
        caption: String? = null,
        frameId: String? = null,
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
                _submitStatus.value = LoadStatus.Success()
                loadFeed() // lam moi feed de thay bai vua dang
            } catch (e: Exception) {
                _submitStatus.value = LoadStatus.Error(e.message ?: "Dang bai that bai.")
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
                _feedStatus.value = LoadStatus.Error(e.message ?: "Khong tai duoc feed.")
            }
        }
    }

    /** Danh dau da xem — goi khi moment hien tren man hinh (fire-and-forget). */
    fun markSeen(momentId: String) {
        viewModelScope.launch {
            try {
                postRepository.markSeen(momentId)
            } catch (_: Exception) {
                // Khong anh huong UX — bo qua loi mark seen
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
}
