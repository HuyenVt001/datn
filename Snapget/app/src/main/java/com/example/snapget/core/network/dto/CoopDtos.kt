package com.example.snapget.core.network.dto

/**
 * Loi moi chup chung (redesign 2026-08-02): moi KHONG kem anh, TTL 5 phut;
 * accept -> ACCEPTED -> 2 ben nop nua anh -> server ghep -> mergedMediaUrl
 * (status COMPLETED) -> moi nguoi tai anh ghep ve va dang bai theo luong thuong.
 */
data class CoopInviteDto(
    val inviteId: String,
    val inviterId: String,
    val inviteeId: String,
    // Nua anh TRAI cua nguoi moi — null khi chua nop
    val inviterMediaUrl: String? = null,
    // Nua anh PHAI cua nguoi nhan
    val inviteeMediaUrl: String? = null,
    // Anh da ghep (co khi status = COMPLETED)
    val mergedMediaUrl: String? = null,
    // PENDING | ACCEPTED | COMPLETED | DECLINED | EXPIRED
    val status: String,
    val createdAt: String,
    // Chi co trong GET /pending (view kem thong tin nguoi moi)
    val inviterName: String? = null,
    val inviterAvatar: String? = null,
)

/** Body POST /moments/coop — gui loi moi (khong kem anh). */
data class CreateCoopInviteRequest(
    val friendUid: String,
)

/** Body POST /moments/coop/{id}/media — nop nua anh cua minh sau khi ACCEPTED. */
data class SubmitCoopMediaRequest(
    val mediaUrl: String,
)
