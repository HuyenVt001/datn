package com.example.snapget.feature.profile.data

import com.example.snapget.core.network.api.MomentApi
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.dto.UserDto
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature profile — goi API server (KHONG cham Firestore).
 * Ho so + danh sach moment (calendar). Server chan xem moment cua nguoi la.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val userApi: UserApi,
    private val momentApi: MomentApi,
) {

    /** Ho so cua minh (co email + personalStreak). */
    suspend fun getMe(): UserDto = userApi.getMe().unwrap()

    /** Ho so cong khai cua user khac (khong email, co personalStreak). */
    suspend fun getPublicProfile(uid: String): UserDto = userApi.getPublicProfile(uid).unwrap()

    /** Moment cua minh cho calendar (100 bai gan nhat). */
    suspend fun getMyMoments(): List<MomentDto> = momentApi.getMine().unwrap().items

    /** Moment cua ban be (server tra 403 neu khong phai ban). */
    suspend fun getUserMoments(uid: String): List<MomentDto> = momentApi.getOfUser(uid).unwrap().items
}
