package com.example.snapget.feature.coop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.coop.data.CoopRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel coop (redesign 2026-08-02): moi khong kem anh (TTL 5 phut) ->
 * accept -> man chup coop POLL trang thai loi moi (~2.5s) -> moi ben upload +
 * nop nua anh -> server ghep -> client tai anh ghep ve va vao luong dang bai.
 */
@HiltViewModel
class CoopViewModel @Inject constructor(
    private val repository: CoopRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    /** uid cua minh — man chup coop dung de biet minh la nguoi moi hay nguoi nhan. */
    val myUid: String? get() = auth.currentUser?.uid

    /** Loi moi chup chung dang cho minh tra loi (banner tren feed). */
    private val _pendingInvites = MutableStateFlow<List<CoopInviteDto>>(emptyList())
    val pendingInvites: StateFlow<List<CoopInviteDto>> = _pendingInvites.asStateFlow()

    /** Loi moi dang mo o man chup coop (cap nhat qua polling refreshInvite). */
    private val _invite = MutableStateFlow<CoopInviteDto?>(null)
    val invite: StateFlow<CoopInviteDto?> = _invite.asStateFlow()

    /** Dang upload/nop nua anh HOAC dang gui/chap nhan loi moi — UI khoa nut. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Loi 1 lan (UI toast roi goi clearError). */
    private val _coopError = MutableStateFlow<String?>(null)
    val coopError: StateFlow<String?> = _coopError.asStateFlow()

    fun loadPending() {
        viewModelScope.launch {
            try {
                _pendingInvites.value = repository.listPending()
            } catch (_: Exception) {
                // Banner tu an neu loi — khong chan feed
            }
        }
    }

    /** Gui loi moi (khong kem anh); thanh cong -> callback de man camera navigate. */
    fun createInvite(friendUid: String, onCreated: (CoopInviteDto) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val invite = repository.sendInvite(friendUid)
                _invite.value = invite
                onCreated(invite)
            } catch (e: Exception) {
                _coopError.value = e.serverMessage("Failed to send invite.")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Chap nhan loi moi -> ACCEPTED; thanh cong -> callback de feed navigate vao man chup. */
    fun acceptInvite(inviteId: String, onAccepted: (CoopInviteDto) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val invite = repository.accept(inviteId)
                _invite.value = invite
                _pendingInvites.value = _pendingInvites.value.filterNot { it.inviteId == inviteId }
                onAccepted(invite)
            } catch (e: Exception) {
                _coopError.value = e.serverMessage("Failed to accept invite.")
                loadPending() // loi moi co the vua het han -> lam moi banner
            } finally {
                _busy.value = false
            }
        }
    }

    fun declineInvite(inviteId: String) {
        // NonCancellable: man goi ham nay co the popBackStack NGAY sau do ->
        // ViewModel bi clear + viewModelScope bi huy. Thieu NonCancellable thi
        // request decline bi cat giua chung — loi moi van PENDING, banner hien lai.
        viewModelScope.launch(NonCancellable) {
            try {
                repository.decline(inviteId)
                _pendingInvites.value = _pendingInvites.value.filterNot { it.inviteId == inviteId }
            } catch (_: Exception) {
                // Bo qua — loi moi tu het han sau 5 phut
            }
        }
    }

    /** Poll trang thai loi moi (~2.5s/lan tu man chup) — loi im lang, giu state cu. */
    fun refreshInvite(inviteId: String) {
        viewModelScope.launch {
            try {
                _invite.value = repository.getInvite(inviteId)
            } catch (_: Exception) {
                // Mat mang thoang qua — cho lan poll sau
            }
        }
    }

    /** Upload + nop nua anh cua minh; response co the da kem mergedMediaUrl. */
    fun submitHalf(inviteId: String, photoFile: File) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val mediaUrl = repository.uploadHalf(photoFile)
                _invite.value = repository.submitMedia(inviteId, mediaUrl)
            } catch (e: Exception) {
                _coopError.value = e.serverMessage("Failed to send your photo.")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Reset khi roi man chup coop (tranh loe loi moi cu khi mo lan sau). */
    fun clearInvite() {
        _invite.value = null
    }

    fun clearError() {
        _coopError.value = null
    }
}
