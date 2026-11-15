package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.BuyShopItemRequest
import com.example.snapget.core.network.dto.BuyShopItemResultDto
import com.example.snapget.core.network.dto.ShopCatalogDto
import com.example.snapget.core.network.dto.ShopPurchaseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Endpoint /shop cua server NestJS (SHOP_PLAN.md — thay /gacha tu 2026-11-15).
 *
 * Mua dut bang Astrite. Gia luon do SERVER quyet: app chi gui `itemId`.
 */
interface ShopApi {

    /** So du Astrite + mat hang dang ban (kem `isOwned` cua minh). */
    @GET("shop/items")
    suspend fun getCatalog(): ApiResponse<ShopCatalogDto>

    /**
     * Mua 1 mat hang. Server tru Astrite + mo khoa trong CUNG 1 transaction;
     * da so huu -> 409, khong du Astrite -> 400.
     */
    @POST("shop/buy")
    suspend fun buy(@Body body: BuyShopItemRequest): ApiResponse<BuyShopItemResultDto>

    /** Lich su mua cua minh (moi nhat truoc). */
    @GET("shop/history")
    suspend fun getHistory(@Query("limit") limit: Int = 50): ApiResponse<List<ShopPurchaseDto>>
}
