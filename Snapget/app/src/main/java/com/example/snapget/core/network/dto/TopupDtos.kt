package com.example.snapget.core.network.dto

/** 1 goi nap dang bat (GET /topup/packages). */
data class TopupPackageDto(
    val packageId: String = "",
    val name: String = "",
    val astrite: Int = 0,
    /** Gia tien VND (so nguyen). */
    val priceVnd: Int = 0,
    val isTest: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * Body POST /topup/orders.
 *
 * ⚠️ CHI co `packageId`. So tien va so Astrite do SERVER tra tu `topupPackages`
 * — app khong bao gio gui len so tien, neu khong thi ai cung tu khai "toi tra
 * 1d, cong cho toi 5 trieu Astrite".
 */
data class CreateTopupOrderRequest(val packageId: String)

/** Ket qua POST /topup/orders. */
data class CreatedTopupOrderDto(
    /**
     * Ma don — so nguyen lon (mili giay + 2 chu so), phai dung `Long`,
     * `Int` se tran.
     */
    val orderCode: Long = 0L,
    /** Link thanh toan PayOS, mo bang Chrome Custom Tabs. */
    val checkoutUrl: String = "",
    val amountVnd: Int = 0,
    val astrite: Int = 0,
    val packageName: String = "",
)

/** Trang thai don nap (GET /topup/orders/:orderCode · GET /topup/history). */
data class TopupOrderDto(
    val orderCode: Long = 0L,
    val packageName: String = "",
    val astrite: Int = 0,
    val amountVnd: Int = 0,
    /** PENDING | PAID | CANCELLED | EXPIRED */
    val status: String = STATUS_PENDING,
    val checkoutUrl: String? = null,
    val createdAt: String = "",
    val paidAt: String? = null,
) {
    val isPaid: Boolean get() = status == STATUS_PAID

    /** Con co the tra tien -> app tiep tuc poll. */
    val isWaiting: Boolean get() = status == STATUS_PENDING

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PAID = "PAID"
    }
}
