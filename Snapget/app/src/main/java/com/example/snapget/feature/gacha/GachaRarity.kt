package com.example.snapget.feature.gacha

import androidx.compose.ui.graphics.Color

/**
 * Mau pham chat gacha (GACHA_PLAN.md muc 0.2).
 *
 * ⚠️ **CO Y khong nam trong `SkinColors`**: day la mau HE THONG cua gacha, phai
 * giu nguyen o moi skin de nguoi choi luon nhan ra bac hiem. Doi skin ma bac SSR
 * doi mau thi mat het y nghia "cam = cuc hiem".
 */
object GachaRarity {
    val N = Color(0xFF9E9E9E)
    val R = Color(0xFF4FC3F7)
    val SR = Color(0xFFB388FF)
    val SSR = Color(0xFFFFA726)

    fun color(tier: String): Color = when (tier) {
        "SSR" -> SSR
        "SR" -> SR
        "R" -> R
        else -> N
    }

    /** Nhan doc duoc cho tung bac (tieng Anh — luat CLAUDE.md muc 8). */
    fun label(tier: String): String = when (tier) {
        "SSR" -> "SSR"
        "SR" -> "SR"
        "R" -> "R"
        else -> "N"
    }
}
