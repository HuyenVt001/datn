package com.example.snapget.core.data

import android.content.Context
import com.example.snapget.core.model.Setting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    companion object {
        private const val PREFS_NAME = "snapget_settings"
    }
}
