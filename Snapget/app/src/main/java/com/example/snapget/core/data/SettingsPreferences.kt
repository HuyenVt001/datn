package com.example.snapget.core.data

import android.content.Context
import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Luu trang thai toggle cua tung setting (theo `id` on dinh trong
 * [SettingDefaults]) vao SharedPreferences.
 *
 * Settings la config UI tinh; chi rieng phan toggle la nguoi dung doi duoc nen
 * chi luu phan do roi PHU len danh sach mac dinh khi doc. Cac muc khong toggle
 * (dieu huong) khong luu gi.
 */
@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Phu trang thai toggle da luu len danh sach settings mac dinh. */
    fun applyOverrides(settings: List<Setting>): List<Setting> = settings.map { setting ->
        if (setting.isToggleable && prefs.contains(setting.id)) {
            setting.copy(isToggled = prefs.getBoolean(setting.id, setting.isToggled))
        } else {
            setting
        }
    }

    /** Luu trang thai toggle moi cho 1 setting. */
    fun setToggle(settingId: String, isToggled: Boolean) {
        prefs.edit().putBoolean(settingId, isToggled).apply()
    }

    /** Doc truc tiep trang thai toggle 1 setting (widget doc live, khong qua list). */
    fun isToggled(settingId: String, default: Boolean): Boolean = prefs.getBoolean(settingId, default)

    private val _themeMode = MutableStateFlow(readThemeMode())

    /**
     * Che do giao dien hien tai. StateFlow tren singleton nay la cau noi giua
     * SettingsViewModel (ghi) va MainActivity/MainViewModel (doc) — khong can DI them.
     */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Doi che do giao dien: persist + emit de toan app recompose ngay. */
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name)!!)
    }.getOrDefault(ThemeMode.DARK)

    companion object {
        private const val PREFS_NAME = "snapget_settings"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
