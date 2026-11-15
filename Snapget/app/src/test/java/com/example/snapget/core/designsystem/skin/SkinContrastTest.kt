package com.example.snapget.core.designsystem.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chot do tuong phan WCAG cho MOI skin trong [SkinRegistry] (Skin v2 — 2026-11-15).
 *
 * Ly do co test: truoc day `textSecondary` cua Snow (#8FA8CC) va Forest (#A8BFA8)
 * truot 4.5:1 tren card kinh ma khong ai phat hien. Them skin moi -> test tu chay.
 *
 * Gioi han: KHONG kiem duoc anh nen (`screenBackground`) vi la bitmap — phan do
 * dua vao `imageScrim` (>= 0.55) + kiem tay. Card kinh duoc kiem tren nen phang.
 */
class SkinContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return if (la > lb) la / lb else lb / la
    }

    /** Kinh bac cao nhat (sang nhat) nam tren nen phang — truong hop xau nhat cho chu trang. */
    private fun AppSkin.worstGlass(): Color {
        val a = atmosphere
        val tint = a.glassTint.copy(alpha = a.glassAlpha + GlassLevel.High.extraAlpha)
        return tint.compositeOver(colors.background)
    }

    private fun assertContrast(skin: AppSkin, what: String, fg: Color, bg: Color, min: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "${skin.displayName}: $what = ${"%.2f".format(ratio)} < $min",
            ratio >= min,
        )
    }

    @Test
    fun `chu chinh va chu phu dat 4_5 tren nen, surface va kinh`() {
        SkinRegistry.all.forEach { skin ->
            val c = skin.colors
            val glass = skin.worstGlass()
            assertContrast(skin, "textPrimary/background", c.textPrimary, c.background, 4.5)
            assertContrast(skin, "textSecondary/background", c.textSecondary, c.background, 4.5)
            assertContrast(skin, "textPrimary/surface", c.textPrimary, c.surface, 4.5)
            assertContrast(skin, "textSecondary/surface", c.textSecondary, c.surface, 4.5)
            assertContrast(skin, "textPrimary/glass", c.textPrimary, glass, 4.5)
            assertContrast(skin, "textSecondary/glass", c.textSecondary, glass, 4.5)
        }
    }

    @Test
    fun `chu tren nut accent dat 4_5, accent tren nen dat 3 (non-text)`() {
        SkinRegistry.all.forEach { skin ->
            val c = skin.colors
            assertContrast(skin, "onAccent/accent", c.onAccent, c.accent, 4.5)
            assertContrast(skin, "accent/background", c.accent, c.background, 3.0)
            assertContrast(skin, "navBarActive/background", c.navBarActive, c.background, 3.0)
        }
    }

    @Test
    fun `scrim anh nen du dam va hat bay khong qua 20`() {
        SkinRegistry.all.forEach { skin ->
            if (skin.images.screenBackground != null) {
                assertTrue("${skin.displayName}: imageScrim qua nhe", skin.atmosphere.imageScrim >= 0.55f)
            }
            assertTrue("${skin.displayName}: qua nhieu hat bay", skin.decor.particleCount <= 15)
        }
    }
}
