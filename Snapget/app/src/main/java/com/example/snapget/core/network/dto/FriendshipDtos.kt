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

/**
 * GET /friendships/invite-link — ma moi + link chia se cua chinh minh.
 * Moi user 1 link, hieu luc 30 ngay (expiresAt); het han server tu sinh ma moi.
 */
data class InviteLinkDto(
    val inviteCode: String,
    val link: String,
    val expiresAt: String?,
)

/**
 * GET /friendships/invite-info/{code} — thong tin nguoi moi de hien dialog
 * "Ket ban voi X?" truoc khi goi connect (nguoi bam link phai xac nhan).
 */
data class InviteInfoDto(
    val uid: String,
    val fullName: String?,
    val avatar: String?,
    val expiresAt: String?,
)

/** Body POST /friendships/connect. */
data class ConnectFriendRequest(
    val inviteCode: String,
)

/**
 * Friendship tra ve sau khi connect / accept.
 * connect gio chi GUI LOI MOI -> status "PENDING" (chu link phai xac nhan);
 * "ACCEPTED" khi 2 ben cung moi nhau hoac sau khi chu link accept.
 */
data class FriendshipDto(
    val pairId: String?,
    val userIds: List<String>?,
    val friendStreak: Int?,
    val status: String?,
)

/** GET /friendships/requests — 1 loi moi ket ban dang cho MINH (chu link) xac nhan. */
data class FriendRequestDto(
    val uid: String,
    val fullName: String?,
    val avatar: String?,
    val requestedAt: String?,
)
