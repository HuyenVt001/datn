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
    fun `every effect except None has a spritesheet`() {
        playable.forEach { effect ->
            assertNotNull(
                "Effect '${effect.displayName}' (id=${effect.id}) has no sheet",
                effect.sheet,
            )
        }
    }

    @Test
    fun `None has no animation and needs no image`() {
        assertEquals(0, TouchEffectRegistry.None.frameCount)
        assertEquals(0, TouchEffectRegistry.None.durationMs)
        assertNull(TouchEffectRegistry.None.sheet)
    }

    @Test
    fun `ids are unique`() {
        val ids = TouchEffectRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `ids do not exceed 10 effects`() {
        // User chot 2026-08-11: toi da 10 hieu ung cham.
        playable.forEach { effect ->
            assertTrue("Effect '${effect.displayName}' has id=${effect.id}, must be within 1..10", effect.id in 1..10)
        }
    }

    @Test
    fun `find with unknown id falls back to None instead of throwing`() {
        // Ban app cu co the nhan id cua vat pham chi co o ban moi hon
        assertEquals(TouchEffectRegistry.None, TouchEffectRegistry.find(99))
        assertEquals(TouchEffectRegistry.None, TouchEffectRegistry.find(-1))
    }

    @Test
    fun `spritesheet grid parameters are valid`() {
        playable.forEach { effect ->
            val name = effect.displayName
            assertTrue("$name: frameCount must be > 0", effect.frameCount > 0)
            assertTrue("$name: columns must be > 0", effect.columns > 0)
            assertTrue(
                "$name: columns (${effect.columns}) must not exceed frameCount (${effect.frameCount})",
                effect.columns <= effect.frameCount,
            )
            assertTrue("$name: fps must be > 0", effect.fps > 0)
            assertTrue("$name: rows must be > 0", effect.rows > 0)
            assertTrue(
                "$name: thumbFrame (${effect.thumbFrame}) must be within 0..${effect.frameCount - 1}",
                effect.thumbFrame in 0 until effect.frameCount,
            )
        }
    }

    @Test
    fun `grid covers the declared frame count`() {
        // rows × columns < frameCount -> frame cuoi nam ngoai luoi, ve ra o trong
        playable.forEach { effect ->
            assertTrue(
                "${effect.displayName}: grid ${effect.columns}×${effect.rows} cannot hold ${effect.frameCount} frames",
                effect.rows * effect.columns >= effect.frameCount,
            )
        }
    }

    @Test
    fun `lifetime is long enough to play every frame`() {
        // playbackMs > durationMs -> `progress` cham tran 1f truoc khi frame cuoi
        // kip hien ra: animation bi cat dau duoi, nguoi dung khong bao gio thay
        // hoa no het.
        playable.forEach { effect ->
            assertTrue("${effect.displayName}: durationMs must be > 0", effect.durationMs > 0)
            assertTrue(
                "${effect.displayName}: chay ${effect.frameCount} frame @${effect.fps}fps can " +
                    "${effect.playbackMs}ms nhung durationMs chi ${effect.durationMs}ms",
                effect.playbackMs <= effect.durationMs,
            )
        }
    }

    @Test
    fun `fade start is valid`() {
        playable.forEach { effect ->
            val name = effect.displayName
            assertTrue("$name: fadeStart must be within [0, 1]", effect.fadeStart in 0f..1f)
            assertTrue("$name: sizeDp must be > 0", effect.sizeDp > 0f)
        }
    }

    @Test
    fun `None is first so it is always the first cell of the Effects tab`() {
        assertEquals(TouchEffectRegistry.NONE_ID, TouchEffectRegistry.all.first().id)
    }
}
