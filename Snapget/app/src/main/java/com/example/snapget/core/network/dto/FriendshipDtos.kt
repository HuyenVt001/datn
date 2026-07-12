package com.example.snapget.core.network.dto

/**
 * DTO cua module /friendships tren server NestJS.
 * Mo hinh ket ban: CHI qua ma moi (khong search, khong loi moi cho duyet) —
 * xem thu muc server/src/friendships tren server.
 */

/** 1 nguoi ban trong GET /friendships (kem friend streak cua cap). */
data class FriendSummaryDto(
    val uid: String,
    val fullName: String?,
    val avatar: String?,
    val friendStreak: Int?,
)

/** GET /friendships/invite-link — ma moi + link chia se cua chinh minh. */
data class InviteLinkDto(
    val inviteCode: String,
    val link: String,
)

/** Body POST /friendships/connect. */
data class ConnectFriendRequest(
    val inviteCode: String,
)

/** Friendship tra ve sau khi connect thanh cong. */
data class FriendshipDto(
    val pairId: String?,
    val userIds: List<String>?,
    val friendStreak: Int?,
    val status: String?,
)
