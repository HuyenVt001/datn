package com.example.snapget.feature.shop

import com.example.snapget.core.network.dto.ShopItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic thuan cua man Shop (SHOP_PLAN.md muc P3): loc mat hang APK khong ve
 * duoc, chia tab, du tien, dang dung.
 */
class ShopUiStateTest {

    private fun item(id: String, type: String, refId: String, price: Int = 100, owned: Boolean = false) = ShopItemDto(itemId = id, itemName = id, itemType = type, refId = refId, price = price, isOwned = owned)

    @Test
    fun `hides skins and effects with refIds unknown to the APK, keeps all frames`() {
        val items = listOf(
            item("frame", ShopItemDto.TYPE_FRAME, "frame_gold"),
            item("snow", ShopItemDto.TYPE_SKIN, "1"),
            item("future-skin", ShopItemDto.TYPE_SKIN, "999"),
            item("default-skin", ShopItemDto.TYPE_SKIN, "0"),
            item("flower", ShopItemDto.TYPE_EFFECT, "1"),
            item("future-effect", ShopItemDto.TYPE_EFFECT, "999"),
            item("none-effect", ShopItemDto.TYPE_EFFECT, "0"),
            item("garbage", ShopItemDto.TYPE_SKIN, "abc"),
            item("unknown-type", "STICKER", "1"),
        )

        val kept = ShopViewModel.supportedItems(items).map { it.itemId }

        assertEquals(listOf("frame", "snow", "flower"), kept)
    }

    @Test
    fun `itemsOf splits tabs correctly, Astrite tab has no items`() {
        val state = ShopUiState(
            items = listOf(
                item("f", ShopItemDto.TYPE_FRAME, "a"),
                item("s", ShopItemDto.TYPE_SKIN, "1"),
                item("e", ShopItemDto.TYPE_EFFECT, "1"),
            ),
        )

        assertEquals(listOf("f"), state.itemsOf(ShopTab.FRAMES).map { it.itemId })
        assertEquals(listOf("s"), state.itemsOf(ShopTab.SKINS).map { it.itemId })
        assertEquals(listOf("e"), state.itemsOf(ShopTab.EFFECTS).map { it.itemId })
        assertTrue(state.itemsOf(ShopTab.ASTRITE).isEmpty())
    }

    @Test
    fun `canAfford includes the exact-balance case`() {
        val state = ShopUiState(astrite = 320)

        assertTrue(state.canAfford(item("x", ShopItemDto.TYPE_FRAME, "a", price = 320)))
        assertFalse(state.canAfford(item("y", ShopItemDto.TYPE_FRAME, "b", price = 321)))
    }

    @Test
    fun `isInUse is true only for the applied skin and effect`() {
        val state = ShopUiState(currentSkinId = 2, currentEffectId = 3)

        assertTrue(state.isInUse(item("s", ShopItemDto.TYPE_SKIN, "2", owned = true)))
        assertFalse(state.isInUse(item("s1", ShopItemDto.TYPE_SKIN, "1", owned = true)))
        assertTrue(state.isInUse(item("e", ShopItemDto.TYPE_EFFECT, "3", owned = true)))
        // Khung khong co khai niem "dang dung" o Shop, ke ca refId trung so
        assertFalse(state.isInUse(item("f", ShopItemDto.TYPE_FRAME, "2", owned = true)))
    }
}
