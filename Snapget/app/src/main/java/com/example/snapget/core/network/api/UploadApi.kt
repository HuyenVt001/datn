package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.UploadResultDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Upload media qua server (server day len Cloudinary, giau secret + chan video >5s). */
interface UploadApi {

    @Multipart
    @POST("upload")
    suspend fun upload(@Part file: MultipartBody.Part): ApiResponse<UploadResultDto>
}
