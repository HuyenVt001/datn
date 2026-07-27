package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.PaginatedData
import com.example.snapget.core.network.dto.ChatGroupDto
import com.example.snapget.core.network.dto.ConversationSummaryDto
import com.example.snapget.core.network.dto.CreateGroupRequest
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.core.network.dto.ReactMessageRequest
import com.example.snapget.core.network.dto.SendMessageRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoint /messages cua server NestJS (chi nhan tin duoc voi ban be). */
interface MessageApi {

    /** Gui tin — 1-1 server tu cong friend streak + push FCM cho nguoi nhan. */
    @POST("messages")
    suspend fun send(@Body body: SendMessageRequest): ApiResponse<MessageDto>

    /** Danh sach hoi thoai 1-1 (tin moi nhat voi tung nguoi, moi nhat truoc). */
    @GET("messages/conversations")
    suspend fun getConversations(): ApiResponse<List<ConversationSummaryDto>>

    /** Thread 1-1: page 1 = doan MOI nhat, items van theo thu tu cu -> moi. */
    @GET("messages/with/{friendUid}")
    suspend fun getThread(
        @Path("friendUid") friendUid: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): ApiResponse<PaginatedData<MessageDto>>

    /** Danh dau da xem (chi nguoi nhan goi duoc). */
    @PATCH("messages/{id}/seen")
    suspend fun markSeen(@Path("id") messageId: String): ApiResponse<Map<String, String>>

    /** Tha reaction len tin nhan (nguoi trong hoi thoai; tha lai cung emoji = go). */
    @POST("messages/{id}/reactions")
    suspend fun react(
        @Path("id") messageId: String,
        @Body body: ReactMessageRequest,
    ): ApiResponse<MessageDto>

    /** Tao nhom chat (<=20 thanh vien, nguoi tao tu vao nhom). */
    @POST("messages/groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): ApiResponse<ChatGroupDto>

    /** Danh sach nhom chat cua minh. */
    @GET("messages/groups")
    suspend fun listGroups(): ApiResponse<List<ChatGroupDto>>

    /** Thread nhom (member-only): page 1 = doan MOI nhat, items cu -> moi. */
    @GET("messages/groups/{groupId}")
    suspend fun getGroupThread(
        @Path("groupId") groupId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): ApiResponse<PaginatedData<MessageDto>>
}
