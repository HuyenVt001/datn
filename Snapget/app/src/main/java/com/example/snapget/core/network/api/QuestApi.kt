package com.example.snapget.core.network.api

import com.example.snapget.core.network.ApiResponse
import com.example.snapget.core.network.dto.TodayQuestsDto
import retrofit2.http.GET

/** Endpoint /quests cua server NestJS. */
interface QuestApi {

    /**
     * 2 quest co dinh cua hom nay + trang thai hoan thanh.
     * Server lazy tao quest theo ngay; GOI ENDPOINT NAY = tu hoan thanh quest LOGIN.
     */
    @GET("quests/today")
    suspend fun getToday(): ApiResponse<TodayQuestsDto>
}
