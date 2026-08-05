package com.example.snapget.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.data.SettingDefaults
import com.example.snapget.core.data.SettingIds
import com.example.snapget.core.data.SettingsPreferences
import com.example.snapget.core.model.Setting
import com.example.snapget.core.network.dto.InviteLinkDto
import com.example.snapget.core.network.dto.UserDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.settings.data.SettingsRepository
import com.example.snapget.feature.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel cua man Settings: danh sach setting (tinh + toggle local),
 * ho so (prefill dialog Edit Name/Birthday) va invite link (Share).
 *
 * Giao dien KHONG con quan ly o day (2026-08-05): Light da xoa, viec chon skin
 * va hieu ung touch chuyen sang man Appearance rieng.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsPreferences: SettingsPreferences,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    private val _settings = MutableStateFlow<List<Setting>>(emptyList())
    val settings: StateFlow<List<Setting>> = _settings.asStateFlow()

    /** Ho so cua minh — prefill dialog; null khi chua tai xong / loi mang. */
    private val _profile = MutableStateFlow<UserDto?>(null)
    val profile: StateFlow<UserDto?> = _profile.asStateFlow()

    /** Trang thai luu Edit Name / Edit Birthday (chi 1 dialog mo tai 1 thoi diem). */
    private val _saveStatus = MutableStateFlow<LoadStatus>(LoadStatus.Init())
    val saveStatus: StateFlow<LoadStatus> = _saveStatus.asStateFlow()

    /** Link moi ket ban cho muc Share Snapget — tai best-effort khi mo man. */
    private val _inviteLink = MutableStateFlow<InviteLinkDto?>(null)
    val inviteLink: StateFlow<InviteLinkDto?> = _inviteLink.asStateFlow()

    init {
        _settings.value = settingsPreferences.applyOverrides(SettingDefaults.visible)
        loadProfile()
        reloadInviteLink()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = settingsRepository.getMe()
            } catch (_: Exception) {
                // Loi mang -> dialog prefill rong, khong chan man hinh
            }
        }
    }

    /** Tai lai invite link (goi lai khi user bam Share ma link chua co). */
    fun reloadInviteLink() {
        viewModelScope.launch {
            try {
                _inviteLink.value = settingsRepository.getInviteLink()
            } catch (_: Exception) {
                // Best-effort; bam Share se thu lai
            }
        }
    }

    /** Cap nhat toggle: luu local + cap nhat state hien thi. */
    fun updateToggle(settingId: String, isToggled: Boolean) {
        settingsPreferences.setToggle(settingId, isToggled)
        _settings.value = _settings.value.map {
            if (it.id == settingId) it.copy(isToggled = isToggled) else it
        }
        if (settingId == SettingIds.STREAK_ON_WIDGET) {
            // Widget doc toggle live tu prefs — chi can ve lai, khong can mang
            viewModelScope.launch { widgetRefresher.updateWidgets() }
        }
    }

    fun saveName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _saveStatus.value is LoadStatus.Loading) return
        viewModelScope.launch {
            _saveStatus.value = LoadStatus.Loading()
            try {
                _profile.value = settingsRepository.updateName(trimmed)
                _saveStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _saveStatus.value = LoadStatus.Error(e.serverMessage("Failed to update name."))
            }
        }
    }

    /** Luu ngay sinh dang ISO yyyy-MM-dd. */
    fun saveBirthday(isoDate: String) {
        if (_saveStatus.value is LoadStatus.Loading) return
        viewModelScope.launch {
            _saveStatus.value = LoadStatus.Loading()
            try {
                _profile.value = settingsRepository.updateBirthday(isoDate)
                _saveStatus.value = LoadStatus.Success()
            } catch (e: Exception) {
                _saveStatus.value = LoadStatus.Error(e.serverMessage("Failed to update birthday."))
            }
        }
    }

    fun resetSaveStatus() {
        _saveStatus.value = LoadStatus.Init()
    }
}
