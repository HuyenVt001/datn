package com.example.snapget.core.designsystem.skin.skins

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.snapget.R
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.ParticleMotion
import com.example.snapget.core.designsystem.skin.SkinAtmosphere
import com.example.snapget.core.designsystem.skin.SkinColors
import com.example.snapget.core.designsystem.skin.SkinDecor
import com.example.snapget.core.designsystem.skin.SkinIcons
import com.example.snapget.core.designsystem.skin.SkinImages
import com.example.snapget.core.designsystem.skin.SkinShapes

/**
 * Skin 2 — **Forest** (xanh la dam · vang be · trang).
 *
 * Bang mau lay tu `Sources/skin-assets/README.md` muc 1.2. Ban o **Shop**
 * (Astrite); mo khoa qua `users.unlockedSkins`.
 *
 * Asset trong `app/src/main/res-skins/skin2_forest/`:
 * - **12 icon vector** (`drawable/skin2_ic_*.xml`) — ve 2026-11-15: glyph be
 *   `#E8D9A8` + la 2 tong; SVG goc o `Sources/skin-assets/skin2_forest/`.
 * - **Skin v2** (art cua user, 2026-11-15): `skin2_bg` (rung nhin tu duoi len),
 *   `skin2_ornament` (dai la/day leo), `skin2_particle` (chiec la bay).
 * - `skin2_thumb`, `skin2_btn_capture` van la placeholder.
 * Thay file = ghi de cung ten, khong phai sua code.
 */
val ForestSkin = AppSkin(
    id = 2,
    displayName = "Forest",
    thumbnail = R.drawable.skin2_thumb,
    images = SkinImages(
        captureButton = R.drawable.skin2_btn_capture,
        screenBackground = R.drawable.skin2_bg,
    ),
    // Card "hinh la": 2 goc bo lon cheo nhau (24/6/24/6) — chi doi card, anh giu nguyen
    shapes = SkinShapes(
        card = RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomEnd = 24.dp, bottomStart = 6.dp),
    ),
    atmosphere = SkinAtmosphere(
        gradient = listOf(Color(0xFF1A3A26), Color(0xFF0E1F16)),
        // Nang xuyen tan la o goc tren trai
        glow = Color(0xFFE8D9A8).copy(alpha = 0.10f),
        glowCenterX = 0.15f,
        glassTint = Color(0xFFE8D9A8),
        glassAlpha = 0.06f,
        glassBorderAlpha = 0.14f,
        // Anh rung SANG (nhieu xanh la non) -> scrim dam hon Snow
        imageScrim = 0.72f,
    ),
    decor = SkinDecor(
        particle = R.drawable.skin2_particle,
        particleMotion = ParticleMotion.DRIFT,
        particleCount = 9,
        particleMinDp = 14f,
        particleMaxDp = 24f,
        ornament = R.drawable.skin2_ornament,
        ornamentAlpha = 0.95f,
        // La xanh tren nen rung xanh -> 10% mac dinh gan nhu chim mat
        patternAlpha = 0.16f,
    ),
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
        // Trang NGA KEM (2026-11-16): chu chinh cung doi tong theo skin, contrast van > 13:1
        onBackground = Color(0xFFF5F0DC),
        onSurface = Color(0xFFF2EEDF),
        onSurfaceVariant = Color(0xFFBCD0BC), // = textSecondary (A8BFA8 truot 4.5:1 tren kinh)
        accent = Color(0xFFE8D9A8),
        accentGold = Color(0xFFD4BE78),
        pill = Color(0xFF1E4530),
        pillTranslucent = Color(0xFF1E4530).copy(alpha = 0.5f),
        overlay = Color.Black.copy(alpha = 0.5f),
        textPrimary = Color(0xFFF5F0DC),
        // #A8BFA8 chi dat 4.3:1 tren card kinh -> nang len 5.3:1 (Skin v2, 2026-11-15)
        textSecondary = Color(0xFFBCD0BC),
        error = Color(0xFFE57373),
        onAccent = Color(0xFF14301F),
        navBarActive = Color(0xFFE8D9A8),
        navBarInactive = Color(0xFFA8BFA8),
        centerButton = Color(0xFF1E4530),
    ),
)
