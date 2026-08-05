package com.example.snapget.core.designsystem.skin.skins

import androidx.compose.ui.graphics.Color
import com.example.snapget.R
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinColors

/**
 * Skin 1 — **Snow** (den · xanh lam dam · trang).
 *
 * Bang mau lay tu `Sources/skin-assets/README.md` muc 1.2. Vat pham **SSR** cua
 * gacha; mo khoa qua `users.unlockedSkins`.
 *
 * Da cam `thumbnail` (tab Skins, 9:16). Icon/nut rieng chua cam — [AppSkin.icons]
 * va [AppSkin.images] de trong nen tu dung ban Material + shape mac dinh. Doi mau
 * da chay day du: khai `accent` xanh la MOI cho vang trong app thanh xanh cung luc.
 */
val SnowSkin = AppSkin(
    id = 1,
    displayName = "Snow",
    thumbnail = R.drawable.skin1_thumb,
    colors = SkinColors(
        background = Color(0xFF0B0F1A),
        surface = Color(0xFF10192B),
        surfaceVariant = Color(0xFF1B3A6B),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFE8F0FF),
        onSurfaceVariant = Color(0xFF8FA8CC),
        accent = Color(0xFF7EC8FF),
        accentGold = Color(0xFFBFE4FF),
        pill = Color(0xFF1B3A6B),
        pillTranslucent = Color(0xFF1B3A6B).copy(alpha = 0.5f),
        overlay = Color.Black.copy(alpha = 0.5f),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8FA8CC),
        error = Color(0xFFFF6B81),
        // Nen accent SANG -> chu tren no phai TOI moi doc duoc (khac Default:
        // vang sang + chu den; o day xanh nhat + chu xanh tham)
        onAccent = Color(0xFF06213D),
    ),
)
