package com.example.snapget.core.network.dto

/** Ho so user tra ve tu GET/PATCH /users/me (khop entity User cua server). */
data class UserDto(
    val uid: String,
    val email: String?,
    val fullName: String?,
    val avatar: String?,
    val joinDate: String?,
    val personalStreak: Int = 0,
    val inviteCode: String?,
    val unlockedFrames: List<String> = emptyList(),
    val fcmTokens: List<String> = emptyList(),
)

/** Body PATCH /users/me — chi doi duoc ten hien thi / avatar. */
data class UpdateUserRequest(
    val fullName: String? = null,
    val avatar: String? = null,
)

/** Body POST /users/me/fcm-tokens. */
data class FcmTokenRequest(val token: String)
