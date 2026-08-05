package com.example.snapget.core.designsystem.skin.skins

import androidx.compose.ui.graphics.Color
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinColors

/**
 * Giao dien den mac dinh — **CHINH XAC** bang mau app dang chay (DESIGN.md).
 *
 * File nay THAY THE `core/designsystem/theme/Color.kt` (da xoa 2026-08-05):
 * palette Gray + SnapYellow + BackgroundPreview gio la token o day, palette
 * Light bi go han (SKIN_PLAN.md muc 4.4). Ten hang so cu ghi trong comment tung
 * dong de doi chieu khi can.
 *
 * Day la moc so sanh: doi cac man sang token phai khong lam xe dich giao dien
 * mot pixel nao. Moi gia tri duoi day deu lay tu hardcode co san trong code,
 * ghi ro so lan xuat hien de doi chieu.
 *
 * skinId = **0**, luon so huu, khong quay gacha ra duoc, dung dau tab Skins.
 */
val DefaultSkin = AppSkin(
    id = 0,
    displayName = "Default",
    colors = SkinColors(
        background = Color(0xFF121212), // GrayBackground — nen app
        surface = Color(0xFF1A1A1A), // GraySurface — the/sheet
        surfaceVariant = Color(0xFF2C2C2C), // GraySurfaceVariant — o trong the
        onBackground = Color(0xFFFFFFFF), // GrayOnBackground
        onSurface = Color(0xFFE0E0E0), // GrayOnSurface
        onSurfaceVariant = Color(0xFFB0B0B0), // GrayOnSurfaceVariant (17 cho)
        accent = Color.Yellow, // SnapYellow — chon/chup/vien active (33 cho)
        accentGold = Color(0xFFFFD700), // SnapGold — streak/badge/moc (19 cho)
        pill = Color(0xFF404137), // nen pill o liu (27 cho — nhieu nhat)
        pillTranslucent = Color(0xFF424242).copy(alpha = 0.5f), // BackgroundPreview
        overlay = Color.Black.copy(alpha = 0.5f), // lop phu tren anh (~20 cho)
        textPrimary = Color.White, // chu/icon chinh (142 cho)
        textSecondary = Color(0xFFB0B0B0), // chu phu/caption/placeholder
        error = Color(0xFFCF6679), // GrayError
        onAccent = Color.Black, // chu den tren nut vang
    ),
)
