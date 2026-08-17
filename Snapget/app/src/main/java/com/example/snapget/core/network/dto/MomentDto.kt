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
    /**
     * CHI co trong response POST /moments (2026-08-15): ket qua AI xac minh anh vua dang
     * so voi quest AI hom nay. null o feed/list va khi khong co gi de xac minh
     * (server tat AI / da xong quest / khong phai anh). Nullable + default = an toan Gson.
     */
    val aiQuest: AiQuestResultDto? = null,
)

/**
 * Ket qua AI xac minh quest (QUEST_AI_PLAN muc 7.2).
 * result: MATCHED (quest AI xong, +30 Astrite) | NOT_MATCHED (bai van dang, quest chua xong,
 * dang bai khac de thu lai) | SKIPPED (AI loi/timeout — im lang).
 */
data class AiQuestResultDto(
    val result: String,
    /** Diem [0,1] cua vat the (khong co khi SKIPPED). */
    val score: Double? = null,
    /** Noi dung quest AI de toast co ngu canh. */
    val questContent: String? = null,
)

/** Body POST /moments. mediaUrl lay tu ket qua POST /upload. */
data class CreateMomentRequest(
    val contentType: String, // PHOTO | VIDEO
    val mediaUrl: String,
    val frameId: String? = null,
    val caption: String? = null,
    // Chong dang TRUNG khi retry sau timeout: app sinh UUID theo TUNG lan vao man
    // dang bai, giu nguyen qua cac lan bam lai — server tra bai cu neu da tao
    val clientRequestId: String? = null,
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
