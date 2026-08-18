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
 * Da cam DU bo asset rieng: `thumbnail`, `captureButton` va 12 icon
 * ([AppSkin.icons]). **Art THAT cua user tu 2026-08-18** (truoc do la placeholder
 * ve giong het ban Material fallback).
 *
 * TOAN BO asset cua skin nay nam trong **`app/src/main/res-skins/skin1_snow/`**
 * (user chot cach gom theo skin; da khai `res.srcDirs` trong build.gradle.kts).
 * ⚠️ Ca 14 file deu la **WebP trong `drawable-nodpi/`**, KHONG con vector XML:
 * art cua skin nay ve theo loi tranh (tuyet, chim canh cut, thong, do bong mem)
 * nen vector khong ta duoc — chi tiet ly do o `SKIN_PLAN.md` muc 6.13.1.
 * File SVG goc user gui cat o `Sources/skin-assets/skin1_snow/` (ngoai duong
 * build). Co ban thiet ke moi thi **ghi de file cung ten**, KHONG can sua code.
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
