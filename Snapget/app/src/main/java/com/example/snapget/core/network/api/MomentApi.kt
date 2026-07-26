package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.PaginatedData
import com.example.snapget.core.network.dto.CreateMomentRequest
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.dto.ReactRequest
import com.example.snapget.core.network.dto.ReactionDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoint /moments (bai dang) cua server NestJS. */
interface MomentApi {

    /** Dang moment. Server tu tang personal streak + gui FCM cho ban be. */
    @POST("moments")
    suspend fun create(@Body body: CreateMomentRequest): ApiResponse<MomentDto>

    /** Feed cua minh + ban be, moi nhat truoc. */
    @GET("moments/feed")
    suspend fun getFeed(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<PaginatedData<MomentDto>>

    /** Moment cua chinh minh (profile: calendar + dem tong). */
    @GET("moments/mine")
    suspend fun getMine(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
    ): ApiResponse<PaginatedData<MomentDto>>

    /** Moment cua 1 user — chi ban be (hoac chinh minh) xem duoc. */
    @GET("moments/user/{uid}")
    suspend fun getOfUser(
        @Path("uid") uid: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
    ): ApiResponse<PaginatedData<MomentDto>>

    /** Danh dau da xem khi luot qua feed. */
    @POST("moments/{id}/seen")
    suspend fun markSeen(@Path("id") momentId: String): ApiResponse<Map<String, String>>

    /** Tha emoji — server cap nhat friend streak voi chu bai. */
    @POST("moments/{id}/reactions")
    suspend fun react(
        @Path("id") momentId: String,
        @Body body: ReactRequest,
    ): ApiResponse<ReactionDto>

    @GET("moments/{id}/reactions")
    suspend fun getReactions(@Path("id") momentId: String): ApiResponse<List<ReactionDto>>
}
