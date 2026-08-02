package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.PaginatedData
import com.example.snapget.core.network.dto.AddGroupMembersRequest
import com.example.snapget.core.network.dto.ChatGroupDetailDto
import com.example.snapget.core.network.dto.ChatGroupDto
import com.example.snapget.core.network.dto.ConversationSummaryDto
import com.example.snapget.core.network.dto.CreateGroupRequest
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.core.network.dto.MuteGroupRequest
import com.example.snapget.core.network.dto.ReactMessageRequest
import com.example.snapget.core.network.dto.SendMessageRequest
import com.example.snapget.core.network.dto.UpdateGroupRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    /** Chi tiet nhom + ho so thanh vien (member-only). */
    @GET("messages/groups/{groupId}/detail")
    suspend fun getGroupDetail(@Path("groupId") groupId: String): ApiResponse<ChatGroupDetailDto>

    /** Doi ten / anh dai dien nhom (moi thanh vien deu doi duoc). */
    @PATCH("messages/groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: String,
        @Body body: UpdateGroupRequest,
    ): ApiResponse<ChatGroupDto>

    /** Them thanh vien vao nhom (chi them duoc BAN BE cua minh — server enforce). */
    @POST("messages/groups/{groupId}/members")
    suspend fun addGroupMembers(
        @Path("groupId") groupId: String,
        @Body body: AddGroupMembersRequest,
    ): ApiResponse<ChatGroupDto>

    /** Xoa thanh vien khoi nhom (chi nguoi tao nhom lam duoc). */
    @DELETE("messages/groups/{groupId}/members/{memberUid}")
    suspend fun removeGroupMember(
        @Path("groupId") groupId: String,
        @Path("memberUid") memberUid: String,
    ): ApiResponse<ChatGroupDto>

    /** Roi nhom (nguoi cuoi cung roi -> server xoa nhom). */
    @POST("messages/groups/{groupId}/leave")
    suspend fun leaveGroup(@Path("groupId") groupId: String): ApiResponse<Map<String, String>>

    /** Bat/tat thong bao nhom cho rieng minh (mutedBy). */
    @PATCH("messages/groups/{groupId}/mute")
    suspend fun muteGroup(
        @Path("groupId") groupId: String,
        @Body body: MuteGroupRequest,
    ): ApiResponse<ChatGroupDto>
}
