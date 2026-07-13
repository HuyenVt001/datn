package com.example.snapget.core.network.dto

/** Loi moi chup chung (server enrich ten/avatar nguoi moi o GET pending). */
data class CoopInviteDto(
    val inviteId: String,
    val inviterId: String,
    val inviteeId: String,
    val inviterMediaUrl: String,
    val status: String, // PENDING | COMPLETED | DECLINED
    val createdAt: String,
    val inviterName: String? = null,
    val inviterAvatar: String? = null,
)

/** Body POST /moments/coop — gui loi moi kem nua anh cua minh. */
data class CreateCoopInviteRequest(
    val friendUid: String,
    val mediaUrl: String,
)

/** Body POST /moments/coop/{id}/accept — nop nua anh con lai. */
data class AcceptCoopInviteRequest(
    val mediaUrl: String,
    val caption: String? = null,
)
