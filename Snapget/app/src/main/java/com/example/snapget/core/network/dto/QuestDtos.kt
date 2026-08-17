package com.example.snapget.core.network.dto

/**
 * 1 quest cua hom nay + trang thai cua minh (GET /quests/today).
 * type: LOGIN | POST_MOMENT (2 quest co dinh, server tu hoan thanh)
 *     | AI_CHALLENGE (quest thu 3 do AI sinh — 2026-08-15, chi co khi server bat AI):
 *       "Chụp một chiếc cốc" — hoan thanh khi anh user DANG LEN FEED duoc AI xac minh
 *       co chua vat the (ket qua ve trong `MomentDto.aiQuest` cua POST /moments).
 * `type` giu la String (khong enum) de server them loai moi app cu van parse duoc.
 */
data class TodayQuestDto(
    val questId: String,
    val type: String,
    val content: String,
    val releaseDate: String,
    val completed: Boolean = false,
    val completedAt: String? = null,
    /** CHI quest AI_CHALLENGE: ten lop vat the (tieng Anh, 1 trong 12 lop) — app khong can hien, content da du. */
    val targetClass: String? = null,
)

/** Ket qua GET /quests/today. */
data class TodayQuestsDto(
    val quests: List<TodayQuestDto> = emptyList(),
    /**
     * So Astrite duoc thuong hom nay khi xong 2/2 quest CO DINH (doi tu `rewardFrameId`
     * ngay 2026-08-05 — thuong quest gio la tien te, khung mo qua gacha).
     * null = chua xong 2/2 quest. Quest AI thuong rieng +30 (khong tinh vao day).
     */
    val rewardAstrite: Int? = null,
)
