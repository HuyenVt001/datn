package com.example.snapget.core.network.dto

/** 1 mat hang trong Cua hang + trang thai so huu cua minh (GET /shop/items). */
data class ShopItemDto(
    val itemId: String = "",
    val itemName: String = "",
    /** FRAME | SKIN | EFFECT */
    val itemType: String = "",
    /** frameId (chuoi) hoac skinId/effectId (so duoi dang chuoi). */
    val refId: String = "",
    /** Gia Astrite — chi de HIEN THI; server tu tinh lai khi mua. */
    val price: Int = 0,
    val imageUrl: String? = null,
    val sortOrder: Int = 0,
    val isOwned: Boolean = false,
) {
    companion object {
        const val TYPE_FRAME = "FRAME"
        const val TYPE_SKIN = "SKIN"
        const val TYPE_EFFECT = "EFFECT"
    }
}

/** Ket qua GET /shop/items. */
data class ShopCatalogDto(
    val astrite: Int = 0,
    val items: List<ShopItemDto> = emptyList(),
)

/** Body POST /shop/buy — CHI co itemId (gia do server quyet). */
data class BuyShopItemRequest(val itemId: String)

/** 1 lan mua (snapshot ten + gia luc mua). */
data class ShopPurchaseDto(
    val purchaseId: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val itemType: String = "",
    val refId: String = "",
    val price: Int = 0,
    val balanceAfter: Int = 0,
    val createdAt: String = "",
)

/** Ket qua POST /shop/buy. */
data class BuyShopItemResultDto(
    val purchase: ShopPurchaseDto = ShopPurchaseDto(),
    val astriteAfter: Int = 0,
)
