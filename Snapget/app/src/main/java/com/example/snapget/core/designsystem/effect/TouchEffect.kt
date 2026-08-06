package com.example.snapget.core.designsystem.effect

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/**
 * Huong bay cua hat sau khi cham — SKIN_PLAN.md muc 2.5.6.
 *
 * `SWAY_*` co them dao ngang hinh sin de hat khong roi thang duon (tuyet/la/bong
 * bong roi thang trong khong nhin rat gia).
 */
enum class EmitDirection {
    /** Toa deu 360° tu diem cham. */
    RADIAL,

    /** Roi xuong, dao ngang. */
    FALL_SWAY,

    /** Bay len, dao ngang. */
    RISE_SWAY,

    /** Ban len roi roi xuong (co trong luc). */
    BURST_FALL,
}

/**
 * 1 hieu ung cham man hinh. **DOC LAP hoan toan voi skin** (SKIN_PLAN.md muc
 * 2.5.1): luu rieng (`touchEffectId`), mo khoa rieng qua gacha, chon o tab
 * rieng — nguoi dung tron tu do "skin xanh + hieu ung lua".
 *
 * Toan bo tham so lay tu phieu o `Sources/skin-assets/effects/EFFECTS.md`.
 */
@Immutable
data class TouchEffect(
    /** 0 = None (khong hieu ung), 1..5. */
    val id: Int,
    /** Ten hien o tab Effects — TIENG ANH (luat CLAUDE.md muc 8). */
    val displayName: String,
    /** So hat sinh ra moi lan cham. */
    val particleCount: Int = 8,
    /** Thoi luong 1 vong doi hat. */
    val durationMs: Int = 800,
    /** Co hat hien thi (dp). */
    val sizeDp: Float = 22f,
    /** Quang duong hat di duoc trong 1 vong doi (dp). */
    val distanceDp: Float = 70f,
    val direction: EmitDirection = EmitDirection.RADIAL,
    /** Bien do dao ngang (dp) — chi co tac dung voi 2 huong `*_SWAY`. */
    val swayDp: Float = 0f,
    /** Toc do xoay (do/giay); 0 = khong xoay. */
    val spinDegPerSec: Float = 0f,
    /** Scale dau -> cuoi vong doi. */
    val scaleFrom: Float = 1f,
    val scaleTo: Float = 0.4f,
    /** Moc thoi gian (0..1) bat dau mo dan. */
    val fadeStart: Float = 0.6f,
    /**
     * `true` = to theo `accent` cua skin dang dung -> hieu ung tu khop mau skin,
     * khoi phai ve moi hieu ung nhieu ban mau.
     */
    val useSkinAccent: Boolean = true,
    /**
     * Anh hat that (PNG trang co alpha, 192×192) trong `res/drawable-nodpi/`,
     * copy tu `Sources/skin-assets/effects/`. Day la **nguon su that ve hinh
     * dang hat** — truoc 2026-08-06 hat duoc ve tay bang `Canvas` nen bong tuyet
     * ra hinh tron, ember ra hinh tron... khong giong anh goc.
     *
     * `null` = chua co anh -> ve tam hinh tron (khong bao gio de hat vo hinh).
     */
    @DrawableRes val particleAsset: Int? = null,
)
