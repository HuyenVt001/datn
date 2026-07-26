package com.example.snapget.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.profile.data.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Ho so hien thi tren man profile (data that tu API). */
data class ProfileUi(
    val uid: String,
    val name: String,
    /** null khi xem profile nguoi khac (server khong tra email). */
    val email: String?,
    val avatar: String,
    val personalStreak: Int,
    val isSelf: Boolean,
)

/**
 * ViewModel cua feature profile — thay mock (streakDays=5) bang data that:
 * personalStreak tu /users/me hoac /users/:uid, calendar tu /moments/mine
 * hoac /moments/user/:uid (server chan nguoi la).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _profile = MutableStateFlow<ProfileUi?>(null)
    val profile: StateFlow<ProfileUi?> = _profile.asStateFlow()

    private val _moments = MutableStateFlow<List<MomentDto>>(emptyList())
    val moments: StateFlow<List<MomentDto>> = _moments.asStateFlow()

    private val _status = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val status: StateFlow<LoadStatus> = _status.asStateFlow()

    /** Trang thai luu ho so (dialog sua ten/avatar). */
    private val _updateStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val updateStatus: StateFlow<LoadStatus> = _updateStatus.asStateFlow()

    /** Tai ho so + moment. [userId] null hoac = uid cua minh -> profile cua minh. */
    fun load(userId: String?) {
        val myUid = auth.currentUser?.uid
        val isSelf = userId == null || userId == myUid
        viewModelScope.launch {
            _status.value = LoadStatus.Loading()
            try {
                val dto = if (isSelf) {
                    profileRepository.getMe()
                } else {
                    profileRepository.getPublicProfile(userId!!)
                }
                _profile.value = ProfileUi(
                    uid = dto.uid,
                    name = dto.fullName ?: "Snapget user",
                    email = dto.email,
                    avatar = dto.avatar.orEmpty(),
                    personalStreak = dto.personalStreak,
                    isSelf = isSelf,
                )
                _moments.value = if (isSelf) {
                    profileRepository.getMyMoments()
                } else {
                    profileRepository.getUserMoments(userId!!)
                }
                _status.value = LoadStatus.Success()
            } catch (e: Exception) {
                _status.value = LoadStatus.Error(e.serverMessage("Couldn't load profile."))
            }
        }
    }

    /**
     * Sua ho so cua minh: upload avatar moi (neu co) roi PATCH ten/avatar.
     * Thanh cong -> cap nhat profile tai cho (khong can reload ca man).
     */
    fun updateProfile(newName: String, avatarFile: File?) {
        if (_updateStatus.value is LoadStatus.Loading) return
        viewModelScope.launch {
            _updateStatus.value = LoadStatus.Loading()
            try {
                val avatarUrl = avatarFile?.let { profileRepository.uploadAvatar(it) }
                val dto = profileRepository.updateProfile(
                    fullName = newName.trim().takeIf { it.isNotEmpty() },
                    avatarUrl = avatarUrl,
                )
                _profile.value = _profile.value?.copy(
                    name = dto.fullName ?: newName,
                    avatar = dto.avatar.orEmpty(),
                )
                _updateStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _updateStatus.value = LoadStatus.Error(e.serverMessage("Failed to update profile."))
            }
        }
    }

    fun resetUpdateStatus() {
        _updateStatus.value = LoadStatus.Init()
    }
}
