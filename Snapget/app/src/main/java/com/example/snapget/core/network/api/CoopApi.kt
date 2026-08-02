package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.network.dto.CreateCoopInviteRequest
import com.example.snapget.core.network.dto.SubmitCoopMediaRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoint /moments/coop — chup chung (redesign 2026-08-02, TTL 5 phut). */
interface CoopApi {

    /** Gui loi moi chup chung (khong kem anh; chi moi duoc ban be). */
    @POST("moments/coop")
    suspend fun createInvite(@Body body: CreateCoopInviteRequest): ApiResponse<CoopInviteDto>

    /** Danh sach loi moi dang cho minh tra loi (banner tren feed). */
    @GET("moments/coop/pending")
    suspend fun listPending(): ApiResponse<List<CoopInviteDto>>

    /** Chi tiet loi moi — man chup coop POLL trang thai (2 ben cung goi duoc). */
    @GET("moments/coop/{id}")
    suspend fun getInvite(@Path("id") inviteId: String): ApiResponse<CoopInviteDto>

    /** Chap nhan loi moi -> ACCEPTED, ca 2 vao man chup coop. */
    @POST("moments/coop/{id}/accept")
    suspend fun accept(@Path("id") inviteId: String): ApiResponse<CoopInviteDto>

    /** Tu choi (nguoi nhan) / huy (nguoi moi) loi moi dang cho. */
    @POST("moments/coop/{id}/decline")
    suspend fun decline(@Path("id") inviteId: String): ApiResponse<Map<String, String>>

    /** Nop nua anh cua minh — du 2 nua server ghep -> mergedMediaUrl. */
    @POST("moments/coop/{id}/media")
    suspend fun submitMedia(
        @Path("id") inviteId: String,
        @Body body: SubmitCoopMediaRequest,
    ): ApiResponse<CoopInviteDto>
}
