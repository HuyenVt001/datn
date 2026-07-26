package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.FcmTokenRequest
import com.example.snapget.core.network.dto.UpdateUserRequest
import com.example.snapget.core.network.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoint /users cua server NestJS (xem server/GUIDE.md muc 5). */
interface UserApi {

    /** Lay ho so cua minh — server TU TAO user doc neu dang nhap lan dau. */
    @GET("users/me")
    suspend fun getMe(): ApiResponse<UserDto>

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateUserRequest): ApiResponse<UserDto>

    /** Dang ky FCM token cua thiet bi de nhan push. */
    @POST("users/me/fcm-tokens")
    suspend fun addFcmToken(@Body body: FcmTokenRequest): ApiResponse<Map<String, String>>

    /** Go FCM token khi logout. */
    @DELETE("users/me/fcm-tokens/{token}")
    suspend fun removeFcmToken(@Path("token") token: String): ApiResponse<Map<String, String>>

    /** Ho so cong khai cua user khac. */
    @GET("users/{uid}")
    suspend fun getPublicProfile(@Path("uid") uid: String): ApiResponse<UserDto>
}
