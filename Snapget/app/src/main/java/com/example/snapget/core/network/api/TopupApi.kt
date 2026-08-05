package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.CreateTopupOrderRequest
import com.example.snapget.core.network.dto.CreatedTopupOrderDto
import com.example.snapget.core.network.dto.TopupOrderDto
import com.example.snapget.core.network.dto.TopupPackageDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoint /topup cua server NestJS (GACHA_PLAN.md muc 5.1).
 *
 * ⚠️ App **khong bao gio** biet ket qua thanh toan tu trang PayOS. Nguon su
 * that duy nhat la webhook PayOS -> server; app chi hoi lai trang thai don qua
 * [getOrder]. Tin vao URL trinh duyet chuyen ve la lo hong: nguoi dung sua
 * duoc thanh dia chi.
 */
interface TopupApi {

    /** Goi nap dang bat. */
    @GET("topup/packages")
    suspend fun listPackages(): ApiResponse<List<TopupPackageDto>>

    /** Tao don + lay link thanh toan. Body CHI co packageId. */
    @POST("topup/orders")
    suspend fun createOrder(@Body body: CreateTopupOrderRequest): ApiResponse<CreatedTopupOrderDto>

    /** Trang thai don cua minh — app poll sau khi dong trang thanh toan. */
    @GET("topup/orders/{orderCode}")
    suspend fun getOrder(@Path("orderCode") orderCode: Long): ApiResponse<TopupOrderDto>

    /** Lich su nap cua minh (moi nhat truoc). */
    @GET("topup/history")
    suspend fun getHistory(@Query("limit") limit: Int = 50): ApiResponse<List<TopupOrderDto>>
}
