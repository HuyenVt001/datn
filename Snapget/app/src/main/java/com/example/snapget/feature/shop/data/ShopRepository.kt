package com.example.snapget.feature.shop.data

import com.example.snapget.core.network.api.ShopApi
import com.example.snapget.core.network.dto.BuyShopItemRequest
import com.example.snapget.core.network.dto.BuyShopItemResultDto
import com.example.snapget.core.network.dto.ShopCatalogDto
import com.example.snapget.core.network.dto.ShopPurchaseDto
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer Cua hang (SHOP_PLAN.md).
 *
 * ⚠️ App khong bao gio tu tru Astrite hay tu mo khoa: moi thay doi so du den tu
 * `POST /shop/buy` (server chay trong transaction). App chi hien ket qua.
 */
@Singleton
class ShopRepository @Inject constructor(
    private val shopApi: ShopApi,
) {
    suspend fun getCatalog(): ShopCatalogDto = shopApi.getCatalog().unwrap()

    suspend fun buy(itemId: String): BuyShopItemResultDto = shopApi.buy(BuyShopItemRequest(itemId)).unwrap()

    suspend fun getHistory(limit: Int = 50): List<ShopPurchaseDto> = shopApi.getHistory(limit).unwrap()
}
