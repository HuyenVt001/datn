package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.AcceptCoopInviteRequest
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.network.dto.CreateCoopInviteRequest
import com.example.snapget.core.network.dto.MomentDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoint /moments/coop (chup chung) cua server NestJS. */
interface CoopApi {

    /** Gui loi moi chup chung cho 1 nguoi ban (server FCM cho ho). */
    @POST("moments/coop")
    suspend fun createInvite(@Body body: CreateCoopInviteRequest): ApiResponse<CoopInviteDto>

    /** Loi moi dang cho minh tra loi. */
    @GET("moments/coop/pending")
    suspend fun listPending(): ApiResponse<List<CoopInviteDto>>

    /** Chap nhan: nop nua anh con lai -> server ghep 2 anh thanh 1 moment chung. */
    @POST("moments/coop/{id}/accept")
    suspend fun accept(
        @Path("id") inviteId: String,
        @Body body: AcceptCoopInviteRequest,
    ): ApiResponse<MomentDto>

    @POST("moments/coop/{id}/decline")
    suspend fun decline(@Path("id") inviteId: String): ApiResponse<Map<String, String>>
}
