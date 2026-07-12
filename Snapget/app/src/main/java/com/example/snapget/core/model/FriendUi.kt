package com.example.snapget.core.model

/**
 * Model hien thi 1 nguoi ban trong sheet ban be (map tu FriendSummaryDto cua API).
 * streak = friend streak chung cua cap ban (0 = chua co / da reset).
 */
data class FriendUi(
    val id: String,
    val name: String,
    val avatar: String = "",
    val streak: Int = 0,
)
