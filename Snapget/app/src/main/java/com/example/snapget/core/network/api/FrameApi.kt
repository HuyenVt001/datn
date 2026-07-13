package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.FrameDto
import retrofit2.http.GET

/** Endpoint /frames cua server NestJS. */
interface FrameApi {

    /** Catalog khung anh + trang thai da mo khoa cua minh. */
    @GET("frames")
    suspend fun list(): ApiResponse<List<FrameDto>>
}
