package com.example.snapget.feature.coop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.coop.data.CoopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CoopViewModel @Inject constructor(
    private val repository: CoopRepository,
) : ViewModel() {

    /** Loi moi chup chung dang cho minh tra loi (banner tren feed). */
    private val _pendingInvites = MutableStateFlow<List<CoopInviteDto>>(emptyList())
    val pendingInvites: StateFlow<List<CoopInviteDto>> = _pendingInvites.asStateFlow()

    /** Trang thai gui loi moi (man CoopSend). */
    private val _sendStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val sendStatus: StateFlow<LoadStatus> = _sendStatus.asStateFlow()

    /** Trang thai chap nhan + ghep anh (man CoopAccept). */
    private val _acceptStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val acceptStatus: StateFlow<LoadStatus> = _acceptStatus.asStateFlow()

    fun loadPending() {
        viewModelScope.launch {
            try {
                _pendingInvites.value = repository.listPending()
            } catch (_: Exception) {
                // Banner tu an neu loi — khong chan feed
            }
        }
    }

    /** Upload nua anh cua minh roi gui loi moi cho ban. */
    fun sendInvite(photoFile: File, friendUid: String) {
        if (_sendStatus.value is LoadStatus.Loading) return
        viewModelScope.launch {
            _sendStatus.value = LoadStatus.Loading()
            try {
                val mediaUrl = repository.uploadHalf(photoFile)
                repository.sendInvite(friendUid, mediaUrl)
                _sendStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _sendStatus.value = LoadStatus.Error(e.serverMessage("Gui loi moi that bai."))
            }
        }
    }

    /** Upload nua anh cua minh roi chap nhan — server ghep 2 anh thanh moment chung. */
    fun acceptInvite(inviteId: String, photoFile: File) {
        if (_acceptStatus.value is LoadStatus.Loading) return
        viewModelScope.launch {
            _acceptStatus.value = LoadStatus.Loading()
            try {
                val mediaUrl = repository.uploadHalf(photoFile)
                repository.accept(inviteId, mediaUrl)
                _acceptStatus.value = LoadStatus.Success()
                loadPending()
            } catch (e: Exception) {
                _acceptStatus.value = LoadStatus.Error(e.serverMessage("Ghep anh that bai."))
            }
        }
    }

    fun declineInvite(inviteId: String) {
        // NonCancellable: man CoopAccept popBackStack NGAY sau khi goi ham nay ->
        // ViewModel bi clear + viewModelScope bi huy. Thieu NonCancellable thi
        // request decline bi cat giua chung — loi moi van PENDING, banner hien lai.
        viewModelScope.launch(NonCancellable) {
            try {
                repository.decline(inviteId)
                _pendingInvites.value = _pendingInvites.value.filterNot { it.inviteId == inviteId }
            } catch (_: Exception) {
                // Bo qua — se thu lai lan sau
            }
        }
    }

    fun resetSendStatus() {
        _sendStatus.value = LoadStatus.Init()
    }

    fun resetAcceptStatus() {
        _acceptStatus.value = LoadStatus.Init()
    }
}
