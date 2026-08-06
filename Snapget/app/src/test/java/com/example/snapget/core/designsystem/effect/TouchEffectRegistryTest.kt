package com.example.snapget.core.designsystem.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rang buoc cua kho hieu ung cham.
 *
 * Cai quan trong nhat: **moi hieu ung quay ra duoc deu phai co anh hat that**.
 * Truoc 2026-08-06 hat duoc ve tay bang `Canvas` nen bong tuyet ra hinh tron,
 * ember ra hinh tron — khong giong anh trong `Sources/skin-assets/effects/`.
 * Them hieu ung moi ma quen cam anh thi test nay do ngay.
 */
class TouchEffectRegistryTest {

    @Test
    fun `moi hieu ung tru None deu co anh hat that`() {
        TouchEffectRegistry.all
            .filter { it.id != TouchEffectRegistry.NONE_ID }
            .forEach { effect ->
                assertNotNull(
                    "Hieu ung '${effect.displayName}' (id=${effect.id}) chua cam particleAsset",
                    effect.particleAsset,
                )
            }
    }

    @Test
    fun `None khong sinh hat va khong can anh`() {
        assertEquals(0, TouchEffectRegistry.None.particleCount)
        assertEquals(0, TouchEffectRegistry.None.durationMs)
        assertNull(TouchEffectRegistry.None.particleAsset)
    }

    @Test
    fun `id khong trung nhau`() {
        val ids = TouchEffectRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `find voi id la roi ve None thay vi nem loi`() {
        // Ban app cu co the nhan id cua vat pham chi co o ban moi hon
        assertEquals(TouchEffectRegistry.None, TouchEffectRegistry.find(99))
        assertEquals(TouchEffectRegistry.None, TouchEffectRegistry.find(-1))
    }

    @Test
    fun `tham so ve hat nam trong khoang hop le`() {
        TouchEffectRegistry.all
            .filter { it.id != TouchEffectRegistry.NONE_ID }
            .forEach { effect ->
                val name = effect.displayName
                assertTrue("$name: particleCount phai > 0", effect.particleCount > 0)
                assertTrue("$name: durationMs phai > 0", effect.durationMs > 0)
                assertTrue("$name: sizeDp phai > 0", effect.sizeDp > 0f)
                // fadeStart = 1f se chia cho 0 khi tinh do mo
                assertTrue("$name: fadeStart phai trong [0, 1)", effect.fadeStart in 0f..0.999f)
                assertTrue("$name: scaleFrom phai > 0", effect.scaleFrom > 0f)
                assertTrue("$name: scaleTo phai > 0", effect.scaleTo > 0f)
            }
    }

    @Test
    fun `None dung dau danh sach de luon la o dau tab Effects`() {
        assertEquals(TouchEffectRegistry.NONE_ID, TouchEffectRegistry.all.first().id)
    }
}
