package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.GachaItemDto
import com.example.snapget.core.network.dto.GachaRollDto
import com.example.snapget.core.network.dto.GachaStateDto
import com.example.snapget.core.network.dto.RollOutcomeDto
import com.example.snapget.core.network.dto.RollRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Endpoint /gacha cua server NestJS (GACHA_PLAN.md muc 5.1). */
interface GachaApi {

    /** So du + pity + gia quay + ti le goc. */
    @GET("gacha/state")
    suspend fun getState(): ApiResponse<GachaStateDto>

    /** Kho vat pham dang bat + `isOwned` cua minh. */
    @GET("gacha/items")
    suspend fun listItems(): ApiResponse<List<GachaItemDto>>

    /** Lich su quay cua minh (moi nhat truoc). */
    @GET("gacha/history")
    suspend fun getHistory(@Query("limit") limit: Int = 50): ApiResponse<List<GachaRollDto>>

    /**
     * Quay 1 hoac 10 lan. Toan bo random chay o SERVER — app chi hien ket qua.
     */
    @POST("gacha/roll")
    suspend fun roll(@Body body: RollRequest): ApiResponse<RollOutcomeDto>
}
