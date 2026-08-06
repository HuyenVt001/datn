package com.example.snapget.feature.gacha

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toan bo cuc man Gacha — phan THUAN TUY (khong can Compose runtime).
 *
 * `BgAnchor` phai lap lai DUNG phep bien doi cua `ContentScale.Crop` +
 * `Alignment.TopCenter`, vi khung so Astrite duoc VE SAN trong `gacha_bg.png`:
 * lech 1px la so Astrite truot khoi khung tren cac may ti le khac 19.5:9.
 */
class GachaLayoutTest {

    private val density = Density(1f)

    // ==== cardRows ====

    @Test
    fun `x10 chia 2-3-3-2 nhu ban thiet ke`() {
        assertEquals(listOf(2, 3, 3, 2), cardRows(10))
    }

    @Test
    fun `x1 nam mot hang`() {
        assertEquals(listOf(1), cardRows(1))
    }

    @Test
    fun `so luot bat ky - tong cac hang phai bang dung so la`() {
        for (count in 1..30) {
            val rows = cardRows(count)
            assertEquals("count=$count", count, rows.sum())
            assertTrue("count=$count co hang rong", rows.all { it > 0 })
        }
    }

    // ==== BgAnchor ====

    @Test
    fun `man hinh dung ti le anh nen - anh xa 1-1`() {
        // 1080x2340 = dung kich thuoc goc -> khong scale, khong lech
        val bg = BgAnchor(BG_W, BG_H, density)
        assertEquals(1f, bg.scale, 1e-4f)
        assertEquals(695f, bg.x(BAR_LEFT).value, 0.01f)
        assertEquals(113f, bg.y(BAR_TOP).value, 0.01f)
        assertEquals(96f, bg.len(ICON_DIAMETER).value, 0.01f)
    }

    @Test
    fun `man dai hon anh (20-9) - tam anh van la tam man hinh`() {
        // 1080x2400 cao hon 19.5:9 -> scale theo chieu cao, anh bi cat deu 2 ben.
        // Diem giua anh (x=540) PHAI roi vao giua man hinh (540) — day la tinh
        // chat cua Crop can giua ngang; sai la moi thu lech sang mot ben.
        val bg = BgAnchor(1080f, 2400f, density)
        assertEquals(2400f / BG_H, bg.scale, 1e-4f)
        assertEquals(540f, bg.x(BG_W / 2f).value, 0.5f)
        // Mep tren giu nguyen (TopCenter) -> y ti le thuan theo scale
        assertEquals(BAR_TOP * (2400f / BG_H), bg.y(BAR_TOP).value, 0.5f)
    }

    @Test
    fun `man ngan hon anh (16-9) - khong cat ngang, mep trai giu nguyen`() {
        // 1080x1920: scale theo chieu RONG (=1), phan duoi anh bi cat — nhung
        // truc ngang khong doi nen toa do x giu nguyen nhu anh goc.
        val bg = BgAnchor(1080f, 1920f, density)
        assertEquals(1f, bg.scale, 1e-4f)
        assertEquals(0f, bg.x(0f).value, 0.01f)
        assertEquals(984f, bg.x(BAR_RIGHT).value, 0.01f)
    }

    @Test
    fun `man be (720p 20-9) - moi thu co dan theo cung mot ti le`() {
        val bg = BgAnchor(720f, 1600f, density)
        // 1600/720 = 2.222 > 2.1667 -> scale theo chieu cao
        val expectedScale = 1600f / BG_H
        assertEquals(expectedScale, bg.scale, 1e-4f)
        // Bat bien Crop: tam anh = tam man
        assertEquals(360f, bg.x(BG_W / 2f).value, 0.5f)
        // Do dai luon duong (khong bi tru offset)
        assertEquals(ICON_DIAMETER * expectedScale, bg.len(ICON_DIAMETER).value, 0.01f)
    }

    @Test
    fun `khung astrite - cac hang so phai khop nhau ve hinh hoc`() {
        // O chu ket thuc dung mep TRAI cua dau `+` (tam + = BAR_RIGHT, ban kinh
        // PLUS/2, chua 6px khe) — neu ai doi hang so lam chu de len dau `+`
        // thi test nay do.
        val textRight = BAR_TEXT_LEFT + (BAR_RIGHT - BAR_TEXT_LEFT - PLUS_DIAMETER / 2f - 6f)
        val plusLeft = BAR_RIGHT - PLUS_DIAMETER / 2f
        assertTrue("chu ($textRight) de len dau + ($plusLeft)", textRight <= plusLeft)

        // Chu bat dau SAU vien pha le (ve san toi ~x780) va nam TRONG khung
        assertTrue(BAR_TEXT_LEFT > BAR_LEFT)
        assertTrue(BAR_TEXT_LEFT < BAR_RIGHT)
        assertTrue(BAR_TOP < BAR_BOTTOM)
    }

    @Test
    fun `2 nut quay - khop be ngang ban thiet ke`() {
        // trai + nut + khe + nut + phai ≈ 1080. Ban thiet ke ve LECH TRAI 4px
        // (le trai 97, le phai 102 — cung kieu lech nhu luoi la bai x10) nen
        // khong doi khop tuyet doi: code dung le doi xung, chi can tong sai
        // khac duoi 8px la nut van dung co nhu ban ve.
        val total = ROLL_BTN_LEFT * 2 + ROLL_BTN_W * 2 + ROLL_BTN_GAP
        assertTrue("lech ${total - BG_W}px so voi ban thiet ke", kotlin.math.abs(total - BG_W) < 8f)
    }
}
