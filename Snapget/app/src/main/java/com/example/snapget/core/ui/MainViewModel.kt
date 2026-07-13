package com.example.snapget.core.ui

import android.util.Log as AndroidLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.data.FirestoreRepository
import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.auth.AuthUser
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
