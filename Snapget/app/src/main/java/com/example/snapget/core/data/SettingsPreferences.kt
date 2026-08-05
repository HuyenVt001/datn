package com.example.snapget.core.data

import android.content.Context
import com.example.snapget.core.designsystem.skin.SkinRegistry
import com.example.snapget.core.model.Setting
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

    // ==== Skin + hieu ung touch (2026-08-05 — thay cho themeMode da xoa) ====
    // Hai lua chon DOC LAP nhau: nguoi dung tron tu do (skin xanh + hieu ung lua).
    // StateFlow tren singleton nay la cau noi giua AppearanceViewModel (ghi) va
    // MainActivity/MainViewModel (doc) — khong can DI them.
    //
    // Prefs cu `theme_mode` con nam lai cung vo hai: khong doc nua, khong can migration.

    private val _skinId = MutableStateFlow(prefs.getInt(KEY_SKIN_ID, SkinRegistry.DEFAULT_ID))

    /** Id skin dang dung. 0 = Default (giao dien den). */
    val skinId: StateFlow<Int> = _skinId.asStateFlow()

    /** Doi skin: persist + emit de toan app ve lai ngay, khong can restart. */
    fun setSkinId(id: Int) {
        prefs.edit().putInt(KEY_SKIN_ID, id).apply()
        _skinId.value = id
    }

    private val _touchEffectId = MutableStateFlow(prefs.getInt(KEY_TOUCH_EFFECT_ID, NO_EFFECT_ID))

    /** Id hieu ung touch dang dung. 0 = None (khong hieu ung). */
    val touchEffectId: StateFlow<Int> = _touchEffectId.asStateFlow()

    fun setTouchEffectId(id: Int) {
        prefs.edit().putInt(KEY_TOUCH_EFFECT_ID, id).apply()
        _touchEffectId.value = id
    }

    companion object {
        private const val PREFS_NAME = "snapget_settings"
        private const val KEY_SKIN_ID = "skin_id"
        private const val KEY_TOUCH_EFFECT_ID = "touch_effect_id"

        /** Hieu ung "None" — luon so huu, mac dinh. */
        const val NO_EFFECT_ID = 0
    }
}
