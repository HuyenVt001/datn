package com.example.snapget.core.network.dto

/** Ho so user tra ve tu GET/PATCH /users/me (khop entity User cua server). */
data class UserDto(
    val uid: String,
    val email: String?,
    val fullName: String?,
    val avatar: String?,
    val joinDate: String?,
    val personalStreak: Int = 0,
    val birthday: String? = null,
    val inviteCode: String?,
    val unlockedFrames: List<String> = emptyList(),
    val fcmTokens: List<String> = emptyList(),
)

/**
 * Body PATCH /users/me — doi duoc ten hien thi / avatar / ngay sinh (yyyy-MM-dd).
 * Gson mac dinh BO field null -> PATCH chi gui field duoc set, khong ghi de field khac.
 */
data class UpdateUserRequest(
    val fullName: String? = null,
    val avatar: String? = null,
    val birthday: String? = null,
)

/** Body POST /users/me/fcm-tokens. */
data class FcmTokenRequest(val token: String)
