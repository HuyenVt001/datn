package com.example.snapget.core.designsystem.effect

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/**
 * 1 hieu ung cham man hinh. **DOC LAP hoan toan voi skin** (SKIN_PLAN.md muc
 * 2.5.1): luu rieng (`touchEffectId`), mo khoa rieng qua gacha, chon o tab
 * rieng — nguoi dung tron tu do "skin xanh + hieu ung hoa".
 *
 * ### Tu 2026-08-11: spritesheet one-shot, KHONG con particle system
 * Truoc day 1 lan cham sinh N hat bay theo quy dao tinh bang `sin/cos`, moi hat
 * la CUNG MOT anh tinh duoc xoay/scale/fade (`particleCount`, `distanceDp`,
 * `EmitDirection`, `swayDp`, `spinDegPerSec`, `scaleFrom/To`...). Nay 1 lan cham
 * phat **dung 1 animation** tai diem cham, chay lan luot qua cac frame cua
 * [sheet]: chuyen dong do ART quyet dinh, khong phai code — doi sheet la doi han
 * cam giac, khong phai do lai chuc tham so.
 *
 * Keo theo: [useSkinAccent] cung bi bo. Sheet da co mau san nen ve nguyen mau,
 * khong tint theo `SkinTheme.colors.accent` nua.
 */
@Immutable
data class TouchEffect(
    /** 0 = None (khong hieu ung), 1..10. */
    val id: Int,
    /** Ten hien o tab Effects — TIENG ANH (luat CLAUDE.md muc 8). */
    val displayName: String,
    /**
     * Spritesheet trong `res/drawable-nodpi/` (PNG-32 co alpha, mau san).
     *
     * **Luoi deu tuyet doi**: [frameCount] frame vuong xep thanh [columns] cot,
     * doc trai→phai roi tren→duoi, khong padding/margin/trim. Co 1 frame duoc
     * suy ra tu anh (`width / columns`) chu khong khai bao tay — khai bao tay la
     * co ngay mot cho ghi sai roi lech ca luoi.
     *
     * `null` = khong co animation (chi dung cho [TouchEffectRegistry.None]).
     */
    @DrawableRes val sheet: Int? = null,
    val frameCount: Int = 0,
    val columns: Int = 1,
    /** Toc do chay frame. 8 frame @ 18fps = 444ms. */
    val fps: Int = 24,
    /**
     * TONG vong doi. Dai hon thoi gian chay frame ([playbackMs]) thi frame cuoi
     * **giu nguyen** cho het gio — de cho [fadeStart] tan dan.
     */
    val durationMs: Int = 0,
    /** Co ve tren man hinh cua CA animation (dp), khong phai 1 hat. */
    val sizeDp: Float = 120f,
    /**
     * Moc thoi gian (0..1) bat dau mo dan; `1f` = khong tan, dung khi art da tu
     * tan ve alpha 0 o frame cuoi.
     *
     * Can moc nay vi khong phai sheet nao cung tu tan: sheet `Flower` frame cuoi
     * la frame DAC NHAT (hoa no het), khong fade ho thi hoa bien mat dot ngot.
     */
    val fadeStart: Float = 1f,
    /**
     * Frame dung lam anh dai dien o the ket qua gacha — thuong la frame animation
     * no to nhat, khong phai frame 0 (frame 0 gan nhu trong tron).
     */
    val thumbFrame: Int = 0,
) {
    /** So hang cua luoi, suy ra tu [frameCount] va [columns]. */
    val rows: Int get() = if (columns <= 0) 0 else (frameCount + columns - 1) / columns

    /** Thoi gian chay het [frameCount] frame. Phai <= [durationMs], neu khong frame cuoi bi cat. */
    val playbackMs: Int get() = if (fps <= 0) 0 else frameCount * 1000 / fps
}
