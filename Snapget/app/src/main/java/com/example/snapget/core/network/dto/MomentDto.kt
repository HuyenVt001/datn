package com.example.snapget.core.network.dto

/** Moment (bai dang) tra ve tu server — khop entity Moment. */
data class MomentDto(
    val momentId: String,
    val userId: String,
    val contentType: String, // PHOTO | VIDEO
    val mediaUrl: String,
    val frameId: String?,
    val caption: String?,
    val postTime: String,
    /** Chup chung (co-op): uid NGUOI NHAN loi moi — moment "cua ca 2" (userId = nguoi moi). */
    val coopUserId: String? = null,
)

/** Body POST /moments. mediaUrl lay tu ket qua POST /upload. */
data class CreateMomentRequest(
    val contentType: String, // PHOTO | VIDEO
    val mediaUrl: String,
    val frameId: String? = null,
    val caption: String? = null,
)

/** Ket qua POST /upload — server day len Cloudinary (video da bi chan >5s). */
data class UploadResultDto(
    val url: String,
    val publicId: String,
    val resourceType: String, // image | video
    val duration: Double?,
    val width: Int?,
    val height: Int?,
)

/** Reaction (emoji bay) trong subcollection cua moment. */
data class ReactionDto(
    val reactionId: String,
    val reactorId: String,
    val emojiType: String,
    val createdAt: String,
)

/** Body POST /moments/{id}/reactions. */
data class ReactRequest(val emojiType: String)
