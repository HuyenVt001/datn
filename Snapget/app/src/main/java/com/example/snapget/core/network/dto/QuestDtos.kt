package com.example.snapget.core.network.dto

/**
 * 1 quest cua hom nay + trang thai cua minh (GET /quests/today).
 * type: LOGIN | POST_MOMENT — 2 quest co dinh, server tu hoan thanh (khong AI, chot 2026-07-13).
 */
data class TodayQuestDto(
    val questId: String,
    val type: String,
    val content: String,
    val releaseDate: String,
    val completed: Boolean = false,
    val completedAt: String? = null,
)

/** Ket qua GET /quests/today. */
data class TodayQuestsDto(
    val quests: List<TodayQuestDto> = emptyList(),
    /** frameId vua duoc thuong hom nay khi xong 2/2 quest (null = chua thuong/het khung). */
    val rewardFrameId: String? = null,
)
