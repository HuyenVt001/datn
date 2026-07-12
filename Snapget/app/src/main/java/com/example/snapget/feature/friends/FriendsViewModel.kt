package com.example.snapget.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.network.dto.InviteLinkDto
import com.example.snapget.feature.friends.data.FriendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel cua feature friends: danh sach ban (kem streak), ma moi QR,
 * ket ban qua quet QR, xoa ban. Du lieu di qua server NestJS /friendships.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository,
) : ViewModel() {

    /** Danh sach ban be (kem friend streak) — nguon cho sheet ban be. */
    private val _friends = MutableStateFlow<List<FriendUi>>(emptyList())
    val friends: StateFlow<List<FriendUi>> = _friends.asStateFlow()

    private val _friendsStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val friendsStatus: StateFlow<LoadStatus> = _friendsStatus.asStateFlow()

    /** Ma moi + link cua chinh minh (de sinh anh QR). */
    private val _inviteLink = MutableStateFlow<InviteLinkDto?>(null)
    val inviteLink: StateFlow<InviteLinkDto?> = _inviteLink.asStateFlow()

    /** Trang thai ket ban khi quet QR — man quet quan sat de bao ket qua. */
    private val _connectStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val connectStatus: StateFlow<LoadStatus> = _connectStatus.asStateFlow()

    /** Tai danh sach ban be. Goi moi lan mo sheet de data luon moi. */
    fun loadFriends() {
        viewModelScope.launch {
            _friendsStatus.value = LoadStatus.Loading()
            try {
                _friends.value = friendsRepository.listFriends().map { dto ->
                    FriendUi(
                        id = dto.uid,
                        name = dto.fullName ?: "Snapget user",
                        avatar = dto.avatar.orEmpty(),
                        streak = dto.friendStreak ?: 0,
                    )
                }
                _friendsStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _friendsStatus.value = LoadStatus.Error(e.message ?: "Khong tai duoc danh sach ban be.")
            }
        }
    }

    /** Tai ma moi cua minh (sinh anh QR trong dialog). Loi thi de null — UI bao lai. */
    fun loadInviteLink() {
        viewModelScope.launch {
            try {
                _inviteLink.value = friendsRepository.getInviteLink()
            } catch (_: Exception) {
                _inviteLink.value = null
            }
        }
    }

    /**
     * Ket ban tu noi dung QR vua quet (link day du hoac ma tho).
     * Thanh cong -> refresh danh sach ban. Loi -> message tieng Viet cua server.
     */
    fun connectFromQr(rawQrContent: String) {
        if (_connectStatus.value is LoadStatus.Loading) return // dang xu ly ma truoc
        viewModelScope.launch {
            _connectStatus.value = LoadStatus.Loading()
            try {
                val code = FriendsRepository.parseInviteCode(rawQrContent)
                friendsRepository.connect(code)
                _connectStatus.value = LoadStatus.Success()
                loadFriends()
            } catch (e: Exception) {
                _connectStatus.value = LoadStatus.Error(e.message ?: "Ket ban that bai.")
            }
        }
    }

    /** Xoa ban (da qua dialog xac nhan o UI) roi refresh danh sach. */
    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            try {
                friendsRepository.removeFriend(friendUid)
                _friends.value = _friends.value.filterNot { it.id == friendUid }
            } catch (e: Exception) {
                _friendsStatus.value = LoadStatus.Error(e.message ?: "Xoa ban that bai.")
            }
        }
    }

    /** Reset trang thai ket ban (goi khi roi man quet / sau khi hien thong bao). */
    fun resetConnectStatus() {
        _connectStatus.value = LoadStatus.Init()
    }
}
