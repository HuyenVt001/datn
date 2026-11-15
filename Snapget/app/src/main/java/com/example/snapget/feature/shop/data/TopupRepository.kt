package com.example.snapget.feature.gacha.data

import com.example.snapget.core.network.api.TopupApi
import com.example.snapget.core.network.dto.CreateTopupOrderRequest
import com.example.snapget.core.network.dto.CreatedTopupOrderDto
import com.example.snapget.core.network.dto.TopupOrderDto
import com.example.snapget.core.network.dto.TopupPackageDto
import com.example.snapget.core.network.unwrap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer nap Astrite (GACHA_PLAN.md muc 4).
 *
 * ⚠️ App chi lam 3 viec: lay danh sach goi, xin link thanh toan, hoi trang thai
 * don. **Khong co duong nao khac cong Astrite** — server chi cong khi webhook
 * PayOS ve, va webhook do phai qua verify chu ky.
 */
@Singleton
class TopupRepository @Inject constructor(
    private val topupApi: TopupApi,
) {
    suspend fun listPackages(): List<TopupPackageDto> = topupApi.listPackages().unwrap()

    suspend fun createOrder(packageId: String): CreatedTopupOrderDto = topupApi.createOrder(CreateTopupOrderRequest(packageId)).unwrap()

    suspend fun getOrder(orderCode: Long): TopupOrderDto = topupApi.getOrder(orderCode).unwrap()

    suspend fun getHistory(limit: Int = 50): List<TopupOrderDto> = topupApi.getHistory(limit).unwrap()
}
