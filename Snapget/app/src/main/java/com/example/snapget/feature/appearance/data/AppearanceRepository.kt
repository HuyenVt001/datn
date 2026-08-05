package com.example.snapget.feature.appearance.data

import com.example.snapget.core.network.api.FrameApi
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.UserDto
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua man Appearance — chi DOC quyen so huu tu server.
 *
 * Skin va hieu ung **nam trong APK**, server chi giu id da mo khoa
 * (`unlockedSkins` / `unlockedEffects`). Lua chon dang dung KHONG luu server —
 * do la thiet lap cuc bo, nam trong `SettingsPreferences`.
 */
@Singleton
class AppearanceRepository @Inject constructor(
    private val frameApi: FrameApi,
    private val userApi: UserApi,
) {

    /** Catalog khung + `isUnlocked` cua minh. */
    suspend fun getFrames(): List<FrameDto> = frameApi.list().unwrap()

    /** Ho so — lay `unlockedSkins` / `unlockedEffects`. */
    suspend fun getMe(): UserDto = userApi.getMe().unwrap()
}
