package com.example.snapget.core.designsystem.skin.skins

import androidx.compose.ui.graphics.Color
import com.example.snapget.R
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinColors
import com.example.snapget.core.designsystem.skin.SkinIcons
import com.example.snapget.core.designsystem.skin.SkinImages

/**
 * Skin 2 — **Forest** (xanh la dam · vang be · trang).
 *
 * Bang mau lay tu `Sources/skin-assets/README.md` muc 1.2. Vat pham **SSR** cua
 * gacha; mo khoa qua `users.unlockedSkins`.
 *
 * Bo asset rieng da cam du nhung noi dung con la **PLACEHOLDER** — thay bang
 * thiet ke that = ghi de file cung ten `res/drawable/skin2_ic_*.xml` +
 * `res/drawable-nodpi/skin2_btn_capture.webp` (xem ghi chu o [SnowSkin]).
 */
val ForestSkin = AppSkin(
    id = 2,
    displayName = "Forest",
    thumbnail = R.drawable.skin2_thumb,
    images = SkinImages(captureButton = R.drawable.skin2_btn_capture),
    icons = SkinIcons(
        camera = R.drawable.skin2_ic_camera,
        send = R.drawable.skin2_ic_send,
        gallery = R.drawable.skin2_ic_gallery,
        flipCamera = R.drawable.skin2_ic_flip_camera,
        close = R.drawable.skin2_ic_close,
        captions = R.drawable.skin2_ic_captions,
        grid = R.drawable.skin2_ic_grid,
        more = R.drawable.skin2_ic_more,
        chat = R.drawable.skin2_ic_chat,
        chevronDown = R.drawable.skin2_ic_chevron_down,
        chevronRight = R.drawable.skin2_ic_chevron_right,
        back = R.drawable.skin2_ic_back,
    ),
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
