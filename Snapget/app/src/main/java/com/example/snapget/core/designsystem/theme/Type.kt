package com.example.snapget.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.snapget.R

/**
 * Font chinh cua app — **Nunito** (DESIGN.md muc 0.8, user chot 2026-11-15).
 * File TTF tinh trong `res/font/` (khong dung Downloadable Fonts de chay offline,
 * khong dung variable font vi minSdk 24). Nguon: Google Fonts, license SIL OFL 1.1.
 */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/**
 * Thang chu M3 day du, **kem fontWeight** (truoc day chi co bodyLarge, man tu set
 * fontWeight tai cho). Nunito tron + dam = cam giac "playful".
 * Quy uoc: chu toi thieu 12sp (labelSmall).
 */
fun appTypography(fontFamily: FontFamily = Nunito): Typography {
    fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = spacing.sp,
    )
    return Typography(
        displayLarge = style(48, 56, FontWeight.Black),
        displayMedium = style(40, 48, FontWeight.Black),
        displaySmall = style(34, 40, FontWeight.ExtraBold),
        headlineLarge = style(30, 38, FontWeight.ExtraBold),
        headlineMedium = style(26, 34, FontWeight.ExtraBold),
        headlineSmall = style(22, 30, FontWeight.ExtraBold),
        titleLarge = style(22, 28, FontWeight.ExtraBold),
        titleMedium = style(17, 24, FontWeight.Bold),
        titleSmall = style(15, 20, FontWeight.Bold),
        bodyLarge = style(16, 24, FontWeight.Normal, 0.2),
        bodyMedium = style(14, 20, FontWeight.Normal, 0.2),
        bodySmall = style(13, 18, FontWeight.Normal, 0.2),
        labelLarge = style(15, 20, FontWeight.ExtraBold, 0.3),
        labelMedium = style(13, 18, FontWeight.Bold, 0.3),
        labelSmall = style(12, 16, FontWeight.Bold, 0.3),
    )
}

/** Giu ten cu cho code/preview dang tham chieu `Typography`. */
val Typography = appTypography()

/**
 * So gamification (streak, Astrite, dem quest) — DESIGN.md 0.8.
 * `tnum`: chu so bang nhau de so DEM LEN khong lam nhay be ngang.
 */
val NumberDisplay = TextStyle(
    fontFamily = Nunito,
    fontWeight = FontWeight.Black,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    fontFeatureSettings = "tnum",
)
