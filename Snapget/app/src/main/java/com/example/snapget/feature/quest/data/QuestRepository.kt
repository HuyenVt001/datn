package com.example.snapget.feature.quest.data

import com.example.snapget.core.network.api.FrameApi
import com.example.snapget.core.network.api.QuestApi
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.TodayQuestsDto
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer cua feature quest — goi API server (KHONG cham Firestore).
 * Quest do server tu hoan thanh; app chi doc trang thai + catalog khung.
 */
@Singleton
class QuestRepository @Inject constructor(
    private val questApi: QuestApi,
    private val frameApi: FrameApi,
    private val userApi: UserApi,
) {

    /** Quest hom nay + trang thai. Goi ham nay = tu hoan thanh quest LOGIN o server. */
    suspend fun getTodayQuests(): TodayQuestsDto = questApi.getToday().unwrap()

    /** Catalog khung anh + trang thai da mo khoa cua minh. */
    suspend fun getFrames(): List<FrameDto> = frameApi.list().unwrap()

    /** Streak ca nhan hien tai (hien banner dau man quest). */
    suspend fun getMyStreak(): Int = userApi.getMe().unwrap().personalStreak
}
