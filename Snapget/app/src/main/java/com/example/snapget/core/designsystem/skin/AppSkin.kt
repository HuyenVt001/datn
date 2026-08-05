package com.example.snapget.core.designsystem.skin

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily

/**
 * 1 giao dien (skin) cua app — SKIN_PLAN.md muc 2.2.
 *
 * Skin duoc dong goi SAN trong APK (khong tai tu server); server chi giu quyen
 * so huu qua `users.unlockedSkins[]` dang so. Vi vay [id] la `Int` va la thu
 * duy nhat server biet.
 *
 * Doi skin = doi TOAN BO token cung luc: khai `accent` mau xanh thi moi cho
 * dang vang trong app thanh xanh, khong sot cho nao (do dung token theo VAI TRO
 * chu khong theo gia tri).
 */
@Immutable
data class AppSkin(
    /** 0 = Default (luon so huu), 1, 2, … */
    val id: Int,
    /** Ten hien tren tab Skins — TIENG ANH (luat CLAUDE.md muc 8). */
    val displayName: String,
    /** Anh 9:16 trong tab Skins. null = ve o mau bang chinh token cua skin. */
    @DrawableRes val thumbnail: Int? = null,
    val colors: SkinColors,
    val icons: SkinIcons = SkinIcons(),
    val shapes: SkinShapes = SkinShapes(),
    val images: SkinImages = SkinImages(),
    /** null = Roboto mac dinh. */
    val fontFamily: FontFamily? = null,
)

/**
 * Token mau theo VAI TRO (khong theo gia tri) — SKIN_PLAN.md muc 3.
 *
 * ⚠️ Sau P2 CAM viet mau hardcode moi trong tang `feature`. Can mau moi thi
 * them token vao day truoc, roi moi dung — neu khong skin khac se sot cho do.
 */
@Immutable
data class SkinColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    /** Mau nhan manh chinh: chon / nut chup / vien active. */
    val accent: Color,
    /** Mau gamification: streak, badge, moc — sac do khac [accent] nhung CUNG he mau. */
    val accentGold: Color,
    /** Nen cac pill / chip / o nhap. */
    val pill: Color,
    /**
     * Nen pill MO dat de len anh/camera (top bar, nut noi tren preview) — phai
     * co alpha de thay hinh ben duoi. Truoc day la hang `BackgroundPreview`.
     */
    val pillTranslucent: Color,
    /** Lop phu toi tren anh (thay `Color.Black.copy(alpha = …)`). */
    val overlay: Color,
    /** Mau chu/icon chinh (thay phan lon `Color.White`). */
    val textPrimary: Color,
    /** Mau chu phu / caption / placeholder. */
    val textSecondary: Color,
    val error: Color,
    /** Chu/icon nam DE LEN accent (nut vang chu den). */
    val onAccent: Color,
) {
    /**
     * Do token sang ColorScheme cua Material 3 — component M3 chua refactor
     * (Button, Switch, TextField…) van doi mau theo skin.
     */
    fun toColorScheme(): ColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surfaceVariant,
        onPrimaryContainer = textPrimary,
        secondary = accentGold,
        onSecondary = onAccent,
        secondaryContainer = surfaceVariant,
        onSecondaryContainer = textPrimary,
        tertiary = accentGold,
        onTertiary = onAccent,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        error = error,
        onError = textPrimary,
    )
}

/**
 * 12 icon Nhom 1 (SKIN_PLAN.md muc 6.13.1) — nhung icon duoc VE RIENG cho skin.
 *
 * Icon Nhom 2/3 KHONG co o day: giu Material icon, chi doi `tint` theo
 * [SkinColors]. `null` = skin nay chua ve icon do -> [SkinIcon] tu fallback ve
 * Material icon, nen skin thieu asset van chay duoc, khong vo man nao.
 */
@Immutable
data class SkinIcons(
    @DrawableRes val camera: Int? = null,
    @DrawableRes val send: Int? = null,
    @DrawableRes val gallery: Int? = null,
    @DrawableRes val flipCamera: Int? = null,
    @DrawableRes val close: Int? = null,
    @DrawableRes val captions: Int? = null,
    @DrawableRes val grid: Int? = null,
    @DrawableRes val more: Int? = null,
    @DrawableRes val chat: Int? = null,
    @DrawableRes val chevronDown: Int? = null,
    @DrawableRes val chevronRight: Int? = null,
    @DrawableRes val back: Int? = null,
)

/**
 * Anh/nen rieng cua skin (SKIN_PLAN.md muc 6.13.2). Tat ca deu TUY CHON —
 * `null` thi ve bang token mau nhu [DefaultSkin] dang lam.
 */
@Immutable
data class SkinImages(
    @DrawableRes val captureButton: Int? = null,
    @DrawableRes val captureButtonPressed: Int? = null,
    @DrawableRes val captureButtonRecording: Int? = null,
    @DrawableRes val bottomBarBackground: Int? = null,
    @DrawableRes val pillBackground: Int? = null,
    @DrawableRes val cardBackground: Int? = null,
    @DrawableRes val bubbleOutgoing: Int? = null,
    @DrawableRes val bubbleIncoming: Int? = null,
    /** null = dung `colors.background` phang. */
    @DrawableRes val screenBackground: Int? = null,
)

/** Bo bo goc dung chung — thay ~110 cho `RoundedCornerShape(...)` rai rac. */
@Immutable
data class SkinShapes(
    val pill: Shape = SkinShapeDefaults.Pill,
    val card: Shape = SkinShapeDefaults.Card,
    val image: Shape = SkinShapeDefaults.Image,
    val sheet: Shape = SkinShapeDefaults.Sheet,
    val input: Shape = SkinShapeDefaults.Input,
)
