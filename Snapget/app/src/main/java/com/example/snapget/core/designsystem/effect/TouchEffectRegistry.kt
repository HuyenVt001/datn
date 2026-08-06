package com.example.snapget.core.designsystem.effect

import com.example.snapget.R

/**
 * Danh sach hieu ung touch dong goi trong APK. Server chi giu ID
 * (`users.unlockedEffects[]`) — day la nguon su that duy nhat ve "hieu ung chay
 * nhu the nao".
 *
 * Tham so lay tu phieu `Sources/skin-assets/effects/EFFECTS.md`; anh hat lay
 * dung file trong phieu do (`res/drawable-nodpi/effectN_particle.png`).
 */
object TouchEffectRegistry {

    /** Hieu ung "khong co gi" — luon so huu, mac dinh, khong quay gacha ra duoc. */
    const val NONE_ID = 0

    val None = TouchEffect(
        id = NONE_ID,
        displayName = "None",
        particleCount = 0,
        durationMs = 0,
    )

    val Snowfall = TouchEffect(
        id = 1,
        displayName = "Snowfall",
        particleCount = 8,
        durationMs = 1200,
        sizeDp = 22f,
        distanceDp = 90f,
        direction = EmitDirection.FALL_SWAY,
        swayDp = 12f,
        spinDegPerSec = 60f,
        scaleFrom = 1f,
        scaleTo = 0.7f,
        fadeStart = 0.6f,
        particleAsset = R.drawable.effect1_particle,
    )

    val Leaf = TouchEffect(
        id = 2,
        displayName = "Leaf",
        particleCount = 6,
        durationMs = 1400,
        sizeDp = 26f,
        distanceDp = 100f,
        direction = EmitDirection.FALL_SWAY,
        swayDp = 20f,
        spinDegPerSec = 120f,
        scaleFrom = 1f,
        scaleTo = 0.8f,
        fadeStart = 0.65f,
        particleAsset = R.drawable.effect2_particle,
    )

    val Sparkle = TouchEffect(
        id = 3,
        displayName = "Sparkle",
        particleCount = 10,
        durationMs = 600,
        sizeDp = 20f,
        distanceDp = 55f,
        direction = EmitDirection.RADIAL,
        spinDegPerSec = 180f,
        scaleFrom = 1f,
        scaleTo = 0.3f,
        fadeStart = 0.5f,
        particleAsset = R.drawable.effect3_particle,
    )

    val Bubble = TouchEffect(
        id = 4,
        displayName = "Bubble",
        particleCount = 7,
        durationMs = 1100,
        sizeDp = 28f,
        distanceDp = 80f,
        direction = EmitDirection.RISE_SWAY,
        swayDp = 10f,
        spinDegPerSec = 0f,
        scaleFrom = 0.6f,
        scaleTo = 1.1f, // phong dan roi vo
        fadeStart = 0.7f,
        particleAsset = R.drawable.effect4_particle,
    )

    val Ember = TouchEffect(
        id = 5,
        displayName = "Ember",
        particleCount = 10,
        durationMs = 900,
        sizeDp = 18f,
        distanceDp = 70f,
        direction = EmitDirection.BURST_FALL,
        spinDegPerSec = 90f,
        scaleFrom = 1f,
        scaleTo = 0.4f,
        fadeStart = 0.55f,
        particleAsset = R.drawable.effect5_particle,
    )

    /** Thu tu trong list = thu tu hien o tab Effects; None luon dung dau. */
    val all: List<TouchEffect> = listOf(None, Snowfall, Leaf, Sparkle, Bubble, Ember)

    /**
     * Tim hieu ung theo id, KHONG BAO GIO nem loi — ban app cu co the nhan id
     * cua vat pham chi co o ban moi hon (SKIN_PLAN.md muc 5).
     */
    fun find(id: Int): TouchEffect = all.firstOrNull { it.id == id } ?: None
}
