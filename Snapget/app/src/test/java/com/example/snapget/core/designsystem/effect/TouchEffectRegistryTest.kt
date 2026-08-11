package com.example.snapget.core.designsystem.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rang buoc cua kho hieu ung cham.
 *
 * Tu 2026-08-11 hieu ung la **spritesheet one-shot**, nen cai de sai nhat khong
 * con la "quen cam anh hat" ma la **khai bao luoi lech voi anh that**: sheet 4
 * cot × 2 hang ma ghi `columns = 3` thi moi frame ve ra se bi cat mat mot phan
 * va lay lan sang frame ben canh — nhin nhu anh bi xe. Test o day khoa nhung
 * bat bien co the kiem tra duoc ma khong can doc file anh (unit test khong co
 * `Resources`); phan "luoi co khop anh that khong" duoc [TouchEffect.rows] +
 * `drawTouchEffectFrame` tu bao ve o runtime bang cach bo ve khi chia lech.
 */
class TouchEffectRegistryTest {

    private val playable = TouchEffectRegistry.all.filter { it.id != TouchEffectRegistry.NONE_ID }

    @Test
    fun `moi hieu ung tru None deu co spritesheet`() {
        playable.forEach { effect ->
            assertNotNull(
                "Hieu ung '${effect.displayName}' (id=${effect.id}) chua cam sheet",
                effect.sheet,
            )
        }
    }

    @Test
    fun `None khong co animation va khong can anh`() {
        assertEquals(0, TouchEffectRegistry.None.frameCount)
        assertEquals(0, TouchEffectRegistry.None.durationMs)
        assertNull(TouchEffectRegistry.None.sheet)
    }

    @Test
    fun `id khong trung nhau`() {
        val ids = TouchEffectRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `id khong vuot tran 10 hieu ung`() {
        // User chot 2026-08-11: toi da 10 hieu ung cham.
        playable.forEach { effect ->
            assertTrue("Hieu ung '${effect.displayName}' co id=${effect.id}, phai trong 1..10", effect.id in 1..10)
        }
    }

    @Test
    fun `find voi id la roi ve None thay vi nem loi`() {
        // Ban app cu co the nhan id cua vat pham chi co o ban moi hon
        assertEquals(TouchEffectRegistry.None, TouchEffectRegistry.find(99))
        assertEquals(TouchEffectRegistry.None, TouchEffectRegistry.find(-1))
    }

    @Test
    fun `tham so luoi spritesheet hop le`() {
        playable.forEach { effect ->
            val name = effect.displayName
            assertTrue("$name: frameCount phai > 0", effect.frameCount > 0)
            assertTrue("$name: columns phai > 0", effect.columns > 0)
            assertTrue(
                "$name: columns (${effect.columns}) khong duoc lon hon frameCount (${effect.frameCount})",
                effect.columns <= effect.frameCount,
            )
            assertTrue("$name: fps phai > 0", effect.fps > 0)
            assertTrue("$name: rows phai > 0", effect.rows > 0)
            assertTrue(
                "$name: thumbFrame (${effect.thumbFrame}) phai trong 0..${effect.frameCount - 1}",
                effect.thumbFrame in 0 until effect.frameCount,
            )
        }
    }

    @Test
    fun `luoi phu du so frame khai bao`() {
        // rows × columns < frameCount -> frame cuoi nam ngoai luoi, ve ra o trong
        playable.forEach { effect ->
            assertTrue(
                "${effect.displayName}: luoi ${effect.columns}×${effect.rows} khong chua du ${effect.frameCount} frame",
                effect.rows * effect.columns >= effect.frameCount,
            )
        }
    }

    @Test
    fun `vong doi du dai de chay het frame`() {
        // playbackMs > durationMs -> `progress` cham tran 1f truoc khi frame cuoi
        // kip hien ra: animation bi cat dau duoi, nguoi dung khong bao gio thay
        // hoa no het.
        playable.forEach { effect ->
            assertTrue("${effect.displayName}: durationMs phai > 0", effect.durationMs > 0)
            assertTrue(
                "${effect.displayName}: chay ${effect.frameCount} frame @${effect.fps}fps can " +
                    "${effect.playbackMs}ms nhung durationMs chi ${effect.durationMs}ms",
                effect.playbackMs <= effect.durationMs,
            )
        }
    }

    @Test
    fun `moc mo dan hop le`() {
        playable.forEach { effect ->
            val name = effect.displayName
            assertTrue("$name: fadeStart phai trong [0, 1]", effect.fadeStart in 0f..1f)
            assertTrue("$name: sizeDp phai > 0", effect.sizeDp > 0f)
        }
    }

    @Test
    fun `None dung dau danh sach de luon la o dau tab Effects`() {
        assertEquals(TouchEffectRegistry.NONE_ID, TouchEffectRegistry.all.first().id)
    }
}
