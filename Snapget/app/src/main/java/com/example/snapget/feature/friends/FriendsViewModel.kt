package com.example.snapget.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.network.dto.InviteInfoDto
import com.example.snapget.core.network.dto.InviteLinkDto
import com.example.snapget.core.network.serverMessage
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

    /** Trang thai GUI LOI MOI (sau khi da xac nhan trong dialog) — UI quan sat de bao ket qua. */
    private val _connectStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val connectStatus: StateFlow<LoadStatus> = _connectStatus.asStateFlow()

    /**
     * Message thanh cong cua lan connect gan nhat — phan biet "da gui loi moi,
     * cho chu link xac nhan" (PENDING) vs "ket ban thanh cong" (2 ben cung moi nhau).
     */
    var lastConnectMessage: String = ""
        private set

    /** Loi moi ket ban dang cho MINH (chu link) xac nhan — section trong sheet ban be. */
    private val _requests = MutableStateFlow<List<FriendUi>>(emptyList())
    val requests: StateFlow<List<FriendUi>> = _requests.asStateFlow()

    /**
     * Trang thai dialog xac nhan ket ban (tu deep link hoac quet QR).
     * info == null && error == null -> dang tai thong tin nguoi moi.
     */
    data class InviteConfirm(
        val code: String,
        val info: InviteInfoDto? = null,
        val error: String? = null,
    )

    private val _inviteConfirm = MutableStateFlow<InviteConfirm?>(null)
    val inviteConfirm: StateFlow<InviteConfirm?> = _inviteConfirm.asStateFlow()

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
                _friendsStatus.value = LoadStatus.Error(e.serverMessage("Couldn't load friends."))
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
     * Mo dialog xac nhan ket ban tu link/QR: parse ma roi tai thong tin nguoi moi
     * (GET /friendships/invite-info). Nguoi dung phai bam "Ket ban" moi goi connect.
     */
    fun startInviteConfirm(rawCodeOrLink: String) {
        val code = FriendsRepository.parseInviteCode(rawCodeOrLink)
        if (code.isEmpty()) return
        if (_inviteConfirm.value?.code == code) return // dialog dang mo cho chinh ma nay
        _inviteConfirm.value = InviteConfirm(code)
        viewModelScope.launch {
            try {
                val info = friendsRepository.getInviteInfo(code)
                _inviteConfirm.value = InviteConfirm(code, info = info)
            } catch (e: Exception) {
                // Ma sai / het han (TTL 30 ngay) -> message tieng Viet cua server
                _inviteConfirm.value = InviteConfirm(code, error = e.serverMessage("Couldn't read the invite."))
            }
        }
    }

    /** Nguoi dung bam "Gui loi moi" trong dialog xac nhan -> goi connect that. */
    fun confirmInvite() {
        val state = _inviteConfirm.value ?: return
        _inviteConfirm.value = null
        connect(state.code, state.info?.fullName)
    }

    /** Dong dialog xac nhan (bam Huy / cham ngoai). */
    fun dismissInviteConfirm() {
        _inviteConfirm.value = null
    }

    /**
     * Goi POST /friendships/connect (GUI LOI MOI) — ket qua day qua [connectStatus],
     * text thanh cong o [lastConnectMessage]. PENDING = cho chu link xac nhan;
     * ACCEPTED (2 ben cung moi nhau) = thanh ban luon -> refresh danh sach ban.
     */
    private fun connect(code: String, inviterName: String?) {
        if (_connectStatus.value is LoadStatus.Loading) return // dang xu ly ma truoc
        viewModelScope.launch {
            _connectStatus.value = LoadStatus.Loading()
            try {
                val friendship = friendsRepository.connect(code)
                if (friendship.status == "ACCEPTED") {
                    lastConnectMessage = "You're now friends! 🎉"
                    loadFriends()
                } else {
                    lastConnectMessage = "Invite sent — waiting for ${inviterName ?: "them"} to confirm!"
                }
                _connectStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _connectStatus.value = LoadStatus.Error(e.serverMessage("Failed to send invite."))
            }
        }
    }

    /** Tai loi moi dang cho minh xac nhan. Goi cung luc voi loadFriends khi mo sheet. */
    fun loadRequests() {
        viewModelScope.launch {
            try {
                _requests.value = friendsRepository.listRequests().map { dto ->
                    FriendUi(
                        id = dto.uid,
                        name = dto.fullName ?: "Snapget user",
                        avatar = dto.avatar.orEmpty(),
                        streak = 0,
                    )
                }
            } catch (_: Exception) {
                // Loi tai loi moi khong chan sheet — giu list cu
            }
        }
    }

    /** Chap nhan loi moi -> thanh ban: bo khoi list loi moi + refresh danh sach ban. */
    fun acceptRequest(requesterUid: String) {
        viewModelScope.launch {
            try {
                friendsRepository.acceptRequest(requesterUid)
                _requests.value = _requests.value.filterNot { it.id == requesterUid }
                loadFriends()
            } catch (e: Exception) {
                _friendsStatus.value = LoadStatus.Error(e.serverMessage("Couldn't accept the invite."))
            }
        }
    }

    /** Tu choi loi moi (xoa im lang — nguoi gui co the moi lai). */
    fun declineRequest(requesterUid: String) {
        viewModelScope.launch {
            try {
                friendsRepository.declineRequest(requesterUid)
                _requests.value = _requests.value.filterNot { it.id == requesterUid }
            } catch (e: Exception) {
                _friendsStatus.value = LoadStatus.Error(e.serverMessage("Couldn't decline the invite."))
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
                _friendsStatus.value = LoadStatus.Error(e.serverMessage("Failed to remove friend."))
            }
        }
    }

    /** Reset trang thai ket ban (goi khi roi man quet / sau khi hien thong bao). */
    fun resetConnectStatus() {
        _connectStatus.value = LoadStatus.Init()
    }
}
