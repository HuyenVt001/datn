package com.example.snapget.feature.gacha.data

import com.example.snapget.core.network.api.GachaApi
import com.example.snapget.core.network.dto.GachaItemDto
import com.example.snapget.core.network.dto.GachaRollDto
import com.example.snapget.core.network.dto.GachaStateDto
import com.example.snapget.core.network.dto.RollOutcomeDto
import com.example.snapget.core.network.dto.RollRequest
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer man Gacha.
 *
 * ⚠️ **App KHONG tu random gi ca** — moi ket qua den tu `POST /gacha/roll`.
 * Astrite doi duoc bang tien that nen random o client la lo hong nghiem trong.
 */
@Singleton
class GachaRepository @Inject constructor(
    private val gachaApi: GachaApi,
) {
    suspend fun getState(): GachaStateDto = gachaApi.getState().unwrap()

    suspend fun listItems(): List<GachaItemDto> = gachaApi.listItems().unwrap()

    suspend fun getHistory(limit: Int = 50): List<GachaRollDto> = gachaApi.getHistory(limit).unwrap()

    suspend fun roll(times: Int): RollOutcomeDto = gachaApi.roll(RollRequest(times)).unwrap()
}
