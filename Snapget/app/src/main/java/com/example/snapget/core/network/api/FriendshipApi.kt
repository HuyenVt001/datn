package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.ConnectFriendRequest
import com.example.snapget.core.network.dto.FriendSummaryDto
import com.example.snapget.core.network.dto.FriendshipDto
import com.example.snapget.core.network.dto.InviteLinkDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoint /friendships cua server NestJS (gioi han 20 ban — server enforce). */
interface FriendshipApi {

    /** Danh sach ban be kem friend streak. */
    @GET("friendships")
    suspend fun listFriends(): ApiResponse<List<FriendSummaryDto>>

    /** Ma moi + link cua chinh minh (server tu sinh neu chua co). */
    @GET("friendships/invite-link")
    suspend fun getInviteLink(): ApiResponse<InviteLinkDto>

    /** Ket ban qua ma moi — server kiem tra gioi han 20 CA HAI phia. */
    @POST("friendships/connect")
    suspend fun connect(@Body body: ConnectFriendRequest): ApiResponse<FriendshipDto>

    /** Xoa ban (mat friend streak chung). */
    @DELETE("friendships/{friendUid}")
    suspend fun removeFriend(@Path("friendUid") friendUid: String): ApiResponse<Map<String, String>>
}
