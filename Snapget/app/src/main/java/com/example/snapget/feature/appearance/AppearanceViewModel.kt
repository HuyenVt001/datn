package com.example.snapget.feature.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.data.SettingsPreferences
import com.example.snapget.core.designsystem.effect.TouchEffect
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinRegistry
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.appearance.data.AppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Trang thai man Appearance (3 tab: Frames · Skins · Effects). */
data class AppearanceUiState(
    val status: LoadStatus = LoadStatus.Init(),
    val frames: List<FrameDto> = emptyList(),
    /** skinId da so huu tu server (skin 0 luon co, khong nam trong day). */
    val ownedSkinIds: Set<Int> = emptySet(),
    /** effectId da so huu tu server (effect 0 luon co, khong nam trong day). */
    val ownedEffectIds: Set<Int> = emptySet(),
    val currentSkinId: Int = SkinRegistry.DEFAULT_ID,
    val currentEffectId: Int = TouchEffectRegistry.NONE_ID,
) {
    val skins: List<AppSkin> get() = SkinRegistry.all
    val effects: List<TouchEffect> get() = TouchEffectRegistry.all

    /** Skin 0 luon so huu, khong bao gio khoa. */
    fun ownsSkin(id: Int): Boolean = id == SkinRegistry.DEFAULT_ID || id in ownedSkinIds

    /** Effect 0 (None) luon so huu. */
    fun ownsEffect(id: Int): Boolean = id == TouchEffectRegistry.NONE_ID || id in ownedEffectIds
}

/**
 * ViewModel man Appearance.
 *
 * Ghi lua chon thang vao [SettingsPreferences] — StateFlow tren singleton do la
 * cau noi toi MainActivity, nen bam la app doi NGAY, khong can restart cung
 * khong can goi mang.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val repository: AppearanceRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppearanceUiState(
            currentSkinId = settingsPreferences.skinId.value,
            currentEffectId = settingsPreferences.touchEffectId.value,
        ),
    )
    val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

    /** Tai khung + quyen so huu skin/hieu ung (2 call song song). */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = LoadStatus.Loading())
            try {
                coroutineScope {
                    val frames = async { repository.getFrames() }
                    val me = async { repository.getMe() }
                    val profile = me.await()
                    _uiState.value = _uiState.value.copy(
                        status = LoadStatus.Success(),
                        frames = frames.await(),
                        ownedSkinIds = profile.unlockedSkins.toSet(),
                        ownedEffectIds = profile.unlockedEffects.toSet(),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = LoadStatus.Error(e.serverMessage("Couldn't load your collection.")),
                )
            }
        }
    }

    /** Ap dung skin. Bo qua neu chua so huu (UI cung da khoa — chan ca 2 lop). */
    fun applySkin(id: Int) {
        if (!_uiState.value.ownsSkin(id)) return
        settingsPreferences.setSkinId(id)
        _uiState.value = _uiState.value.copy(currentSkinId = id)
    }

    /** Ap dung hieu ung cham. */
    fun applyEffect(id: Int) {
        if (!_uiState.value.ownsEffect(id)) return
        settingsPreferences.setTouchEffectId(id)
        _uiState.value = _uiState.value.copy(currentEffectId = id)
    }
}
