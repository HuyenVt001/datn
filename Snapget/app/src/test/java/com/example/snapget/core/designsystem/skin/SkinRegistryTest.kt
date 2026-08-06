package com.example.snapget.core.designsystem.skin

import com.example.snapget.core.designsystem.component.bottombar.submitPhotoBar
import com.example.snapget.core.designsystem.component.bottombar.takePhotoBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rang buoc cua kho skin + day noi P5 (icon/nut chup rieng theo skin).
 *
 * Diem de vo nhat: `BottomNavItem.skinIcon` la lambda chon field tu [SkinIcons]
 * — chon nham field (vd "Photo Library" tro vao `camera`) thi compile van qua,
 * chi sai luc chay. Test chot tung selector bang SkinIcons gia co gia tri rieng
 * tung field.
 */
class SkinRegistryTest {

    @Test
    fun `id khong trung nhau va Default dung dau`() {
        val ids = SkinRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(SkinRegistry.DEFAULT_ID, SkinRegistry.all.first().id)
    }

    @Test
    fun `find voi id la roi ve Default thay vi nem loi`() {
        assertEquals(SkinRegistry.DEFAULT_ID, SkinRegistry.find(99).id)
        assertEquals(SkinRegistry.DEFAULT_ID, SkinRegistry.find(-1).id)
    }

    @Test
    fun `skin mo khoa qua gacha co nut chup rieng, Default thi khong`() {
        // Default ve nut chup bang token mau -> khong can anh
        assertNull(SkinRegistry.find(SkinRegistry.DEFAULT_ID).images.captureButton)
        // Snow (1) + Forest (2) da cam anh nut chup (P5)
        assertNotNull(SkinRegistry.find(1).images.captureButton)
        assertNotNull(SkinRegistry.find(2).images.captureButton)
    }

    @Test
    fun `skin gacha khai DU 12 icon - quen field nao la do ngay`() {
        // Default co y de trong (fallback Material la giao dien chuan cua no);
        // con skin ban bang tien that thi bo icon phai du — thay the ve sau chi
        // viec ghi de file, khong dung vao code.
        SkinRegistry.all.filter { it.id != SkinRegistry.DEFAULT_ID }.forEach { skin ->
            val icons = skin.icons
            listOf(
                "camera" to icons.camera, "send" to icons.send,
                "gallery" to icons.gallery, "flipCamera" to icons.flipCamera,
                "close" to icons.close, "captions" to icons.captions,
                "grid" to icons.grid, "more" to icons.more,
                "chat" to icons.chat, "chevronDown" to icons.chevronDown,
                "chevronRight" to icons.chevronRight, "back" to icons.back,
            ).forEach { (name, res) ->
                assertNotNull("skin '${skin.displayName}' thieu icon '$name'", res)
            }
        }
    }

    @Test
    fun `selector skinIcon cua tung item tro dung field`() {
        // Moi field 1 gia tri rieng -> selector lay nham field la lech so ngay
        val probe = SkinIcons(
            camera = 1, send = 2, gallery = 3, flipCamera = 4, close = 5,
            captions = 6, grid = 7, more = 8, chat = 9, chevronDown = 10,
            chevronRight = 11, back = 12,
        )

        fun selectorOf(title: String) = (takePhotoBar + submitPhotoBar)
            .first { it.title == title }
            .skinIcon!!

        assertEquals(3, selectorOf("Photo Library")(probe))
        assertEquals(4, selectorOf("Change camera")(probe))
        assertEquals(5, selectorOf("Cancel")(probe))
        assertEquals(6, selectorOf("Captions List")(probe))
    }

    @Test
    fun `skin chua ve icon - selector tra null de fallback Material`() {
        val empty = SkinIcons()
        (takePhotoBar + submitPhotoBar)
            .mapNotNull { it.skinIcon }
            .forEach { selector -> assertNull(selector(empty)) }
    }
}
