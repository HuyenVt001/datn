package com.example.snapget.core.network.dto

/** Bo dem bao hiem tung bac (GET /gacha/state). */
data class GachaPityDto(
    val R: Int = 0,
    val SR: Int = 0,
    val SSR: Int = 0,
)

/** Ti le GOC (khong phai ti le thuc sau pity) — dung sinh popup Rule. */
data class GachaRatesDto(
    val N: Double = 0.0,
    val R: Double = 0.0,
    val SR: Double = 0.0,
    val SSR: Double = 0.0,
)

/**
 * Trang thai man Gacha (GET /gacha/state).
 *
 * Gia + ti le + pity deu lay TU SERVER chu khong hardcode lai o app: popup
 * "Rule gacha" sinh tu chinh du lieu nay nen so hien ra luon khop so dang chay.
 */
data class GachaStateDto(
    val astrite: Int = 0,
    val pity: GachaPityDto = GachaPityDto(),
    val pityLimit: GachaPityDto = GachaPityDto(),
    val costSingle: Int = 0,
    val costTen: Int = 0,
    val tenTimes: Int = 10,
    val rates: GachaRatesDto = GachaRatesDto(),
    val refunds: GachaPityDto = GachaPityDto(),
)

/** 1 vat pham trong kho quay + trang thai so huu cua minh (GET /gacha/items). */
data class GachaItemDto(
    val itemId: String,
    val itemName: String,
    /** FRAME | EFFECT | SKIN */
    val itemType: String,
    /** R | SR | SSR */
    val rarity: String,
    val imageUrl: String? = null,
    /** frameId (chuoi) hoac skinId/effectId (so duoi dang chuoi). */
    val refId: String,
    val isOwned: Boolean = false,
)

/** 1 ket qua le trong 1 luot quay. */
data class RollResultDto(
    /** N | R | SR | SSR */
    val tier: String,
    /** Bac N: so Astrite nhan duoc. Bac khac: null. */
    val astriteAmount: Int? = null,
    val itemId: String? = null,
    val itemName: String? = null,
    val itemType: String? = null,
    val refId: String? = null,
    val imageUrl: String? = null,
    val isDuplicate: Boolean = false,
    val refundAstrite: Int = 0,
)

/** Ket qua POST /gacha/roll — 1 lan bam nut (x10 van la 1 doi tuong, 10 phan tu). */
data class RollOutcomeDto(
    val rollId: String = "",
    val rollType: String = "SINGLE",
    val cost: Int = 0,
    val results: List<RollResultDto> = emptyList(),
    val refundTotal: Int = 0,
    val astriteAfter: Int = 0,
)

/** Body POST /gacha/roll. */
data class RollRequest(val times: Int)

/** 1 dong lich su quay cua minh (GET /gacha/history). */
data class GachaRollDto(
    val rollId: String = "",
    val rollType: String = "SINGLE",
    val cost: Int = 0,
    val results: List<RollResultDto> = emptyList(),
    val refundTotal: Int = 0,
    val balanceAfter: Int = 0,
    val createdAt: String = "",
)
