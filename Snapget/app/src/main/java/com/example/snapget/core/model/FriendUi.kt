package com.example.snapget.core.model

/**
 * Model hien thi 1 nguoi ban trong sheet ban be (map tu FriendSummaryDto cua API).
 * (2026-11-15) Friend streak DA BO — chi giu streak ca nhan.
 */
data class FriendUi(
    val id: String,
    val name: String,
    val avatar: String = "",
)
