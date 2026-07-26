package com.example.snapget.feature.settings.data

import com.example.snapget.core.network.api.FriendshipApi
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.InviteLinkDto
import com.example.snapget.core.network.dto.UpdateUserRequest
import com.example.snapget.core.network.dto.UserDto
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature settings — goi API server (KHONG cham Firestore).
 * Repository mong rieng de feature/settings khong import cheo feature/profile, feature/friends.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val userApi: UserApi,
    private val friendshipApi: FriendshipApi,
) {

    /** Ho so cua minh — prefill dialog Edit Name / Edit Birthday. */
    suspend fun getMe(): UserDto = userApi.getMe().unwrap()

    /** Doi ten hien thi (PATCH /users/me — field null giu nguyen). */
    suspend fun updateName(fullName: String): UserDto = userApi.updateMe(UpdateUserRequest(fullName = fullName)).unwrap()

    /** Doi ngay sinh yyyy-MM-dd (PATCH /users/me). */
    suspend fun updateBirthday(birthday: String): UserDto = userApi.updateMe(UpdateUserRequest(birthday = birthday)).unwrap()

    /** Link moi ket ban (dung cho muc Share Snapget). */
    suspend fun getInviteLink(): InviteLinkDto = friendshipApi.getInviteLink().unwrap()
}
