package com.example.snapget.core.designsystem.skin.skins

import androidx.compose.ui.graphics.Color
import com.example.snapget.R
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinColors
import com.example.snapget.core.designsystem.skin.SkinIcons
import com.example.snapget.core.designsystem.skin.SkinImages

/**
 * Skin 1 — **Snow** (den · xanh lam dam · trang).
 *
 * Bang mau lay tu `Sources/skin-assets/README.md` muc 1.2. Vat pham **SSR** cua
 * gacha; mo khoa qua `users.unlockedSkins`.
 *
 * Da cam DU bo asset rieng (2026-08-06): `thumbnail`, `captureButton` va 12 icon
 * ([AppSkin.icons]). ⚠️ Noi dung hien tai la **PLACEHOLDER** (icon ve giong het
 * ban Material fallback, nut chup la vong tron accent) — muc dich la de duong
 * ong chay san.
 *
 * TOAN BO asset cua skin nay nam trong **`app/src/main/res-skins/skin1_snow/`**
 * (user chot cach gom theo skin; da khai `res.srcDirs` trong build.gradle.kts):
 * icon vector o `drawable/skin1_ic_*.xml`, nut chup + thumbnail o
 * `drawable-nodpi/`. Co thiet ke that thi **ghi de file cung ten** trong thu
 * muc do, KHONG can sua dong code nao.
 */
val SnowSkin = AppSkin(
    id = 1,
    displayName = "Snow",
    thumbnail = R.drawable.skin1_thumb,
    images = SkinImages(captureButton = R.drawable.skin1_btn_capture),
    icons = SkinIcons(
        camera = R.drawable.skin1_ic_camera,
        send = R.drawable.skin1_ic_send,
        gallery = R.drawable.skin1_ic_gallery,
        flipCamera = R.drawable.skin1_ic_flip_camera,
        close = R.drawable.skin1_ic_close,
        captions = R.drawable.skin1_ic_captions,
        grid = R.drawable.skin1_ic_grid,
        more = R.drawable.skin1_ic_more,
        chat = R.drawable.skin1_ic_chat,
        chevronDown = R.drawable.skin1_ic_chevron_down,
        chevronRight = R.drawable.skin1_ic_chevron_right,
        back = R.drawable.skin1_ic_back,
    ),
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
