package com.example.snapget.core.designsystem.skin

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
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
    /** Khong khi nen: gradient, vang sang, kinh (Skin v2 — SKIN_PLAN.md muc "Atmosphere"). */
    val atmosphere: SkinAtmosphere = SkinAtmosphere(),
    /** Trang tri: hat bay + dai ornament mep tren. Mac dinh = khong co. */
    val decor: SkinDecor = SkinDecor(),
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
    /** Mau icon tab footer dang chon. */
    val navBarActive: Color = textPrimary,
    /** Mau icon tab footer chua chon. */
    val navBarInactive: Color = onSurfaceVariant,
    // ---- Token CHUC NANG "Playful Dark" (DESIGN.md muc 0.7 — chot 2026-11-15) ----
    // Co gia tri mac dinh nen skin cu khong phai sua; skin muon hop tong thi khai rieng.
    /** Thanh cong: quest xong, check, thao tac OK. */
    val success: Color = Color(0xFF58CC7A),
    /** Rieng streak 🔥 — tach khoi [accentGold] de streak luon "nong". */
    val streak: Color = Color(0xFFFF9F1C),
    /** Thong tin trung tinh, link phu. */
    val info: Color = Color(0xFF4FC3F7),
    /** Badge chua doc o MOI noi (user chot = accentGold, bo do cua footer). */
    val badge: Color = accentGold,
    val onBadge: Color = onAccent,
    /** Day "day" cua nut chunky — accent toi 30% (DESIGN.md 0.3). */
    val accentShadow: Color = accent.darken(0.7f),
    // ---- Bac be mat M3 (Skin v2 — 2026-11-15) ----
    // Truoc day KHONG khai -> ModalBottomSheet/AlertDialog/Card/NavigationBar cua M3
    // roi ve mau xam ngau tim baseline, lech tong skin. Mac dinh suy tu token san co.
    /** Nen ModalBottomSheet. */
    val surfaceContainerLow: Color = surface,
    /** Nen Card/NavigationBar M3. */
    val surfaceContainer: Color = lerp(surface, surfaceVariant, 0.5f),
    /** Nen AlertDialog. */
    val surfaceContainerHigh: Color = surfaceVariant,
    /** Vien o nhap OutlinedTextField, divider M3. */
    val outline: Color = onSurfaceVariant,
    val outlineVariant: Color = surfaceVariant,
    /** Lop toi sau sheet/dialog. */
    val scrim: Color = Color.Black.copy(alpha = 0.6f),
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
        surfaceDim = background,
        surfaceBright = surfaceVariant,
        surfaceContainerLowest = background,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
    )
}

/** Nhan RGB voi [factor] (giu alpha) — dung tao mau "day" cho nut chunky. */
fun Color.darken(factor: Float): Color = Color(
    red = red * factor,
    green = green * factor,
    blue = blue * factor,
    alpha = alpha,
)

/**
 * 12 icon Nhom 1 (SKIN_PLAN.md muc 6.13.1) — nhung icon duoc VE RIENG cho skin.
 *
 * Icon Nhom 2/3 KHONG co o day: giu Material icon, chi doi `tint` theo
 * [SkinColors]. `null` = skin nay chua ve icon do -> [SkinIcon] tu fallback ve
 * Material icon, nen skin thieu asset van chay duoc, khong vo man nao.
 *
 * ⚠️ Icon o day ve NGUYEN MAU trong file (chot 2026-08-14): designer to mau san
 * cho ca bo cho khop bang mau skin, app khong tint. Chi nhanh fallback Material
 * moi an `tint` theo token — xem [SkinIcon].
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

/**
 * Khong khi nen cua skin (Skin v2 "Atmosphere" — SKIN_PLAN.md, chot 2026-11-15).
 *
 * Ve boi [SkinBackdrop] MOT lan o goc app (sau NavHost). Thu tu uu tien:
 * anh [SkinImages.screenBackground] (+ lop toi [imageScrim]) -> [gradient] -> mau
 * phang `colors.background`. [glow] la vang sang radial ve de len tren.
 *
 * Kinh ([glassTint]/[glassAlpha]) KHONG blur that: `Modifier.blur` chi chay tu
 * API 31 va chi lam mo chinh no -> dung lop to mau trong + vien 1px, chay tu API 24.
 */
@Immutable
data class SkinAtmosphere(
    /** Gradient doc (tren -> duoi). It hon 2 mau = dung `colors.background` phang. */
    val gradient: List<Color> = emptyList(),
    /** Vang sang radial; [Color.Unspecified] = khong co. Alpha nam san trong mau. */
    val glow: Color = Color.Unspecified,
    /** Tam vang sang theo ti le man hinh (0..1). */
    val glowCenterX: Float = 0.85f,
    val glowCenterY: Float = 0.0f,
    /** Mau to kinh cho card/pill dat tren backdrop. */
    val glassTint: Color = Color.White,
    /** Do mo lop kinh bac co ban (bac 2/3 cong them 0.04/0.08). */
    val glassAlpha: Float = 0.06f,
    /** Do mo vien 1px cua kinh (cung mau [glassTint]). */
    val glassBorderAlpha: Float = 0.10f,
    /**
     * Do mo lop `colors.background` phu len anh nen. Do bang contrast: >= 0.65
     * thi chu phu #B0B0B0 dat 4.5:1 ca tren vung anh sang (#C0C0C0).
     */
    val imageScrim: Float = 0.65f,
)

/** Kieu chuyen dong cua hat bay tren nen. */
enum class ParticleMotion {
    /** Roi thang, lac nhe, xoay cham — tuyet, hoa. */
    FALL,

    /** Roi cheo, lac rong, lat qua lat lai — la. */
    DRIFT,
}

/**
 * Trang tri cua skin — tat ca TUY CHON, `null` = khong ve (app van dep nho
 * [SkinAtmosphere]). Asset nam trong `res-skins/skin<N>_<ten>/drawable-nodpi/`.
 */
@Immutable
data class SkinDecor(
    /** Hat bay (128x128, nen trong). Ve NGUYEN MAU, khong tint. */
    @DrawableRes val particle: Int? = null,
    val particleMotion: ParticleMotion = ParticleMotion.FALL,
    /** So hat cung luc — giu <= 15 (guideline "Excessive Motion"). */
    val particleCount: Int = 12,
    val particleMinDp: Float = 10f,
    val particleMaxDp: Float = 20f,
    /** Dai trang tri mep tren (1440x360, nen trong, day mo dan). */
    @DrawableRes val ornament: Int? = null,
    val ornamentAlpha: Float = 0.9f,
    /** Do mo particle khi rai thanh hoa van nen chat ([skinChatWallpaper]). */
    val patternAlpha: Float = 0.10f,
)
