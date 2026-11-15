package com.example.snapget.feature.coop

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.constants.COOP_INVITE_TTL_SECONDS
import com.example.snapget.core.fcm.CoopPushEvents
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
 * Loi moi dang cho + MOC HET HAN tren dong ho may (SystemClock.elapsedRealtime —
 * khong bi anh huong khi user doi gio he thong). Tinh tu `remainingSeconds` server
 * tra ve luc nhan response, nen may lech gio van dem nguoc dung.
 */
data class PendingCoopInvite(
    val invite: CoopInviteDto,
    val deadlineElapsedMs: Long,
) {
    fun remainingMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long = (deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0)
}

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

    /** Loi moi chup chung dang cho minh tra loi (the loi moi o Camera + Feed). */
    private val _pendingInvites = MutableStateFlow<List<PendingCoopInvite>>(emptyList())
    val pendingInvites: StateFlow<List<PendingCoopInvite>> = _pendingInvites.asStateFlow()

    /** inviteId dang gui accept — the hien spinner tren nut Accept cua dung the do. */
    private val _acceptingInviteId = MutableStateFlow<String?>(null)
    val acceptingInviteId: StateFlow<String?> = _acceptingInviteId.asStateFlow()

    /** Loi moi dang mo o man chup coop (cap nhat qua polling refreshInvite). */
    private val _invite = MutableStateFlow<CoopInviteDto?>(null)
    val invite: StateFlow<CoopInviteDto?> = _invite.asStateFlow()

    /** Dang upload/nop nua anh HOAC dang gui/chap nhan loi moi — UI khoa nut. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Loi 1 lan (UI toast roi goi clearError). */
    private val _coopError = MutableStateFlow<String?>(null)
    val coopError: StateFlow<String?> = _coopError.asStateFlow()

    init {
        // Push chup chung khi app dang mo (2026-11-16): cap nhat ngay, khong doi poll
        viewModelScope.launch {
            CoopPushEvents.events.collect { event ->
                when (event.type) {
                    CoopPushEvents.TYPE_COOP_INVITE -> loadPending()
                    CoopPushEvents.TYPE_COOP_CANCELLED -> {
                        // Go the ngay, roi dong bo lai voi server
                        event.inviteId?.let { id -> removePending(id) }
                        loadPending()
                    }
                    // Man chup coop: doi phuong accept / anh ghep xong -> refresh ngay
                    CoopPushEvents.TYPE_COOP_ACCEPTED, CoopPushEvents.TYPE_COOP_DONE -> {
                        val current = _invite.value
                        if (current != null && (event.inviteId == null || event.inviteId == current.inviteId)) {
                            refreshInvite(current.inviteId)
                        }
                    }
                }
            }
        }
    }

    fun loadPending() {
        viewModelScope.launch {
            try {
                val now = SystemClock.elapsedRealtime()
                _pendingInvites.value = repository.listPending()
                    .map { invite ->
                        val remainingSec = invite.remainingSeconds ?: COOP_INVITE_TTL_SECONDS
                        PendingCoopInvite(invite, now + remainingSec * 1000)
                    }
                    .filter { it.remainingMs(now) > 0 }
            } catch (_: Exception) {
                // The tu an neu loi — khong chan man dang mo
            }
        }
    }

    /** Dem nguoc ve 0 -> go the ngay (khong doi lan poll sau moi bien mat). */
    fun removePending(inviteId: String) {
        _pendingInvites.value = _pendingInvites.value.filterNot { it.invite.inviteId == inviteId }
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
            _acceptingInviteId.value = inviteId
            try {
                val invite = repository.accept(inviteId)
                _invite.value = invite
                removePending(inviteId)
                onAccepted(invite)
            } catch (e: Exception) {
                _coopError.value = e.serverMessage("Failed to accept invite.")
                loadPending() // loi moi co the vua het han / bi huy -> lam moi the
            } finally {
                _busy.value = false
                _acceptingInviteId.value = null
            }
        }
    }

    fun declineInvite(inviteId: String) {
        // NonCancellable: man goi ham nay co the popBackStack NGAY sau do ->
        // ViewModel bi clear + viewModelScope bi huy. Thieu NonCancellable thi
        // request decline bi cat giua chung — loi moi van PENDING, banner hien lai.
        // Go the NGAY (lac quan) — user bam Decline la muon the bien mat luon
        removePending(inviteId)
        viewModelScope.launch(NonCancellable) {
            try {
                repository.decline(inviteId)
            } catch (_: Exception) {
                // Bo qua — loi moi tu het han sau 5 phut
            }
        }
    }

    /** Poll trang thai loi moi (~2.5s/lan tu man chup) — loi im lang, giu state cu. */
    fun refreshInvite(inviteId: String) {
        viewModelScope.launch {
            try {
                _invite.value = preferNewer(repository.getInvite(inviteId))
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
                _invite.value = preferNewer(repository.submitMedia(inviteId, mediaUrl))
            } catch (e: Exception) {
                _coopError.value = e.serverMessage("Failed to send your photo.")
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Nop LAI dung URL nua anh da co de kich hoat ghep lan nua — dung khi server
     * ghep loi va da revert ve ACCEPTED (man chup hien nut "Tap to retry").
     */
    fun retryMerge(inviteId: String, myMediaUrl: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                _invite.value = preferNewer(repository.submitMedia(inviteId, myMediaUrl))
            } catch (e: Exception) {
                _coopError.value = e.serverMessage("Failed to merge photos.")
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Chong response VE MUON ghi de state moi hon (poll 2.5s dan xen voi submit —
     * GET ban ra TRUOC khi submit xong co the VE SAU response submit; nuot guard
     * nay thi nua anh vua nop "bien mat", nut chup hien lai, user nop trung lan 2):
     * 1. Da co anh ghep -> KHONG bao gio lui ve ban chua co (mergedMediaUrl la moc cuoi).
     * 2. Cung status nhung IT media hon = ban cu — giu ban hien tai.
     * Doi status van luon tin server (ke ca COMPLETED -> ACCEPTED: server revert
     * khi ghep loi, phai nhan de con hien nut retry).
     */
    private fun preferNewer(latest: CoopInviteDto): CoopInviteDto {
        val cur = _invite.value
        if (cur == null || cur.inviteId != latest.inviteId) return latest
        if (cur.mergedMediaUrl != null && latest.mergedMediaUrl == null) return cur
        if (latest.status == cur.status && mediaCount(latest) < mediaCount(cur)) return cur
        return latest
    }

    private fun mediaCount(invite: CoopInviteDto): Int = listOfNotNull(invite.inviterMediaUrl, invite.inviteeMediaUrl, invite.mergedMediaUrl).size

    /** Reset khi roi man chup coop (tranh loe loi moi cu khi mo lan sau). */
    fun clearInvite() {
        _invite.value = null
    }

    fun clearError() {
        _coopError.value = null
    }
}
