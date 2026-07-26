package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.ConnectFriendRequest
import com.example.snapget.core.network.dto.FriendRequestDto
import com.example.snapget.core.network.dto.FriendSummaryDto
import com.example.snapget.core.network.dto.FriendshipDto
import com.example.snapget.core.network.dto.InviteInfoDto
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

    /** Ma moi + link cua chinh minh (hieu luc 30 ngay — server tu sinh neu chua co/het han). */
    @GET("friendships/invite-link")
    suspend fun getInviteLink(): ApiResponse<InviteLinkDto>

    /** Thong tin nguoi moi tu ma moi — cho dialog xac nhan truoc khi connect. */
    @GET("friendships/invite-info/{code}")
    suspend fun getInviteInfo(@Path("code") code: String): ApiResponse<InviteInfoDto>

    /**
     * GUI LOI MOI ket ban qua ma moi (status tra ve PENDING — chu link phai
     * xac nhan; ACCEPTED neu 2 ben cung moi nhau). Server check gioi han 20 CA HAI phia.
     */
    @POST("friendships/connect")
    suspend fun connect(@Body body: ConnectFriendRequest): ApiResponse<FriendshipDto>

    /** Loi moi ket ban dang cho minh (chu link) xac nhan. */
    @GET("friendships/requests")
    suspend fun listRequests(): ApiResponse<List<FriendRequestDto>>

    /** Chap nhan loi moi — server kiem tra LAI gioi han 20 ca 2 phia. */
    @POST("friendships/requests/{requesterUid}/accept")
    suspend fun acceptRequest(@Path("requesterUid") requesterUid: String): ApiResponse<FriendshipDto>

    /** Tu choi loi moi (xoa im lang — nguoi gui co the moi lai). */
    @POST("friendships/requests/{requesterUid}/decline")
    suspend fun declineRequest(@Path("requesterUid") requesterUid: String): ApiResponse<Map<String, String>>

    /** Xoa ban (mat friend streak chung). */
    @DELETE("friendships/{friendUid}")
    suspend fun removeFriend(@Path("friendUid") friendUid: String): ApiResponse<Map<String, String>>
}
