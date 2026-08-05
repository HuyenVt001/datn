package com.example.snapget.core.designsystem.skin.skins

import androidx.compose.ui.graphics.Color
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinColors

/**
 * Skin 2 — **Forest** (xanh la dam · vang be · trang).
 *
 * Bang mau lay tu `Sources/skin-assets/README.md` muc 1.2. Vat pham **SSR** cua
 * gacha; mo khoa qua `users.unlockedSkins`.
 */
val ForestSkin = AppSkin(
    id = 2,
    displayName = "Forest",
    colors = SkinColors(
        background = Color(0xFF0E2416),
        surface = Color(0xFF14301F),
        surfaceVariant = Color(0xFF1E4530),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFF2EEDF),
        onSurfaceVariant = Color(0xFFA8BFA8),
        accent = Color(0xFFE8D9A8),
        accentGold = Color(0xFFD4BE78),
        pill = Color(0xFF1E4530),
        pillTranslucent = Color(0xFF1E4530).copy(alpha = 0.5f),
        overlay = Color.Black.copy(alpha = 0.5f),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA8BFA8),
        error = Color(0xFFE57373),
        onAccent = Color(0xFF14301F),
    ),
)
