package com.example.snapget.core.ui

import android.util.Log as AndroidLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.data.FirestoreRepository
import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.auth.AuthUser
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.unwrap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dung chung: user hien tai + settings (UI cua app).
 * DA DON GOD-VM (2026-07-13): toan bo phan posts/friends/messages doc Firestore
 * truc tiep da xoa — cac feature doc qua API server (PostViewModel,
 * FriendsViewModel, MessageViewModel...).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: FirestoreRepository,
    // Ho so that (fullName/avatar) nam o server — Firebase Auth photoUrl thuong
    // RONG voi tai khoan email/password nen khong dung lam nguon avatar duoc
    private val userApi: UserApi,
) : ViewModel() {

    private val _settings = MutableStateFlow<List<Setting>>(emptyList())
    val settings: StateFlow<List<Setting>> = _settings

    // Current user StateFlow
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    fun fetchCurrentUser() {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                _currentUser.value = user
                // Overlay ten + avatar tu GET /users/me (nguon chuan sau khi
                // sua ho so) — API loi thi giu ban Firebase, khong chan UI
                if (user != null) {
                    try {
                        val profile = userApi.getMe().unwrap()
                        _currentUser.value = user.copy(
                            name = profile.fullName?.takeIf { it.isNotBlank() } ?: user.name,
                            avatar = profile.avatar?.takeIf { it.isNotBlank() } ?: user.avatar,
                        )
                    } catch (e: Exception) {
                        AndroidLog.d("MainViewModel", "Profile overlay failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                AndroidLog.d("MainViewModel", "Error fetching user: ${e.message}")
            }
        }
    }

    fun getAllSetting() {
        viewModelScope.launch {
            try {
                val result = repository.getAllSetting()
                _settings.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Updates the toggle state of a setting in the local state and database.
     *
     * @param settingId The ID of the setting to update.
     * @param isToggled The new toggle state for the setting.
     */
    fun updateSettingToggle(settingId: String, isToggled: Boolean) {
        viewModelScope.launch {
            try {
                // Find the setting in the current list
                val currentSetting = _settings.value.find { it.id == settingId } ?: return@launch

                // Create updated setting with new toggle state
                val updatedSetting = currentSetting.copy(isToggled = isToggled)

                // Call repository to update in database
                val result = repository.updateSetting(updatedSetting)

                // Update the local state
                _settings.value = _settings.value.map {
                    if (it.id == settingId) result else it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
