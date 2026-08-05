package com.example.snapget.core.ui

import android.util.Log as AndroidLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.data.FirestoreRepository
import com.example.snapget.core.data.SettingsPreferences
import com.example.snapget.core.designsystem.effect.TouchEffect
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinRegistry
import com.example.snapget.core.model.auth.AuthUser
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.unwrap
import com.example.snapget.feature.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel dung chung: user hien tai + skin dang ap dung.
 * DA DON GOD-VM (2026-07-13): toan bo phan posts/friends/messages doc Firestore
 * truc tiep da xoa — cac feature doc qua API server (PostViewModel,
 * FriendsViewModel, MessageViewModel...).
 * Phan danh sach settings da chuyen sang SettingsViewModel (feature/settings, 2026-07-26).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: FirestoreRepository,
    // Ho so that (fullName/avatar) nam o server — Firebase Auth photoUrl thuong
    // RONG voi tai khoan email/password nen khong dung lam nguon avatar duoc
    private val userApi: UserApi,
    settingsPreferences: SettingsPreferences,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    /**
     * Skin dang ap dung — MainActivity doc de bom vao AppTheme.
     *
     * Map id -> AppSkin ngay tai day: id la de server/prefs, con UI can object
     * that. `SkinRegistry.find` chiu duoc id la (vat pham cua ban app moi hon)
     * bang cach roi ve Default thay vi crash.
     */
    val skin: StateFlow<AppSkin> = settingsPreferences.skinId
        .map { SkinRegistry.find(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SkinRegistry.find(settingsPreferences.skinId.value),
        )

    /** Hieu ung cham dang dung — MainActivity doc de bom vao TouchEffectOverlay. */
    val touchEffect: StateFlow<TouchEffect> = settingsPreferences.touchEffectId
        .map { TouchEffectRegistry.find(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            TouchEffectRegistry.find(settingsPreferences.touchEffectId.value),
        )

    // Current user StateFlow
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    fun fetchCurrentUser() {
        // Mo app / login xong -> widget refresh 1 lan (worker tu no-op neu chua dang nhap)
        widgetRefresher.refreshNow()
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
}
