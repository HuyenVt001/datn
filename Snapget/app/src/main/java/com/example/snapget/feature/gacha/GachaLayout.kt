package com.example.snapget.feature.gacha

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/** Kich thuoc goc cua `gacha_bg.png` — moi toa do trong file nay tinh theo he nay. */
internal const val BG_W = 1080f
internal const val BG_H = 2340f

// ==== Khung so Astrite: VE SAN trong anh nen, do tu file goc ====
internal const val BAR_LEFT = 695f
internal const val BAR_RIGHT = 984f
internal const val BAR_TOP = 113f
internal const val BAR_BOTTOM = 180f
internal const val BAR_CENTER_Y = (BAR_TOP + BAR_BOTTOM) / 2f

/** Vien pha le ve san de len dau trai thanh -> so phai bat dau sau no. */
internal const val BAR_TEXT_LEFT = 798f

/** Nut `+` nap tien — dat dung mep phai khung (user chot). */
internal const val PLUS_DIAMETER = 68f

/** 2 icon tu them o hang tren: back goc trai, luat ngay ben trai khung Astrite. */
internal const val ICON_DIAMETER = 96f
internal const val BACK_CENTER_X = 92f
internal const val RULES_CENTER_X = 562f

// ==== 2 nut quay: do tu `PreviewGachaScreen.png` ====
internal const val ROLL_BTN_W = 386f
internal const val ROLL_BTN_H = 139f
internal const val ROLL_BTN_LEFT = 97f

/** Khoang ho giua 2 nut (593 - 483 trong ban thiet ke). */
internal const val ROLL_BTN_GAP = 110f

/** Day nut cach day anh nen (2340 - 2144). */
internal const val ROLL_BTN_BOTTOM = 196f

// ==== La bai ket qua ====
internal const val CARD_W = 217f
internal const val CARD_H = 364f

/** Buoc cot / buoc hang cua luoi 10 la (do tu `PreviewGachaScreen_Gachax10.png`). */
internal const val CARD_COL_PITCH = 310f
internal const val CARD_ROW_PITCH = 410f

/**
 * O trong khung mat truoc — noi dat anh vat pham. Do bang cach do tu tam la bai
 * ra 4 phia toi khi cham net ve: x 22..193, y 55..270. Lay hep hon mot chut cho
 * anh khong dinh hoa van.
 */
internal const val CARD_SLOT_W_RATIO = 0.58f
internal const val CARD_SLOT_H_RATIO = 0.42f

/** Tam o dat anh, tinh theo ti le canh la bai. */
internal const val CARD_SLOT_CENTER_Y_RATIO = 0.42f

/**
 * Doi toa do TRONG `gacha_bg.png` -> toa do tren man hinh.
 *
 * ⚠️ Vi sao phai co lop nay: anh nen **da ve san khung so Astrite** o goc phai
 * tren. Anh duoc ve bang `ContentScale.Crop` nen tren may co ti le khac 19.5:9
 * no bi phong to roi cat bot — neo UI theo % man hinh se lam so Astrite truot ra
 * ngoai khung ve san. Day la phep bien doi Y HET cua `Crop` + `Alignment.TopCenter`:
 * cat deu 2 ben theo chieu ngang, **giu nguyen mep tren** de hang header (khung
 * Astrite) khong bao gio bi cat mat.
 *
 * Rieng 2 nut quay KHONG dung lop nay ma neo theo day MAN HINH: may 16:9 co phan
 * duoi anh bi cat, neo theo anh la nut nam ngoai man hinh.
 */
internal class BgAnchor(
    screenWidthPx: Float,
    screenHeightPx: Float,
    private val density: Density,
) {
    val scale: Float = max(screenWidthPx / BG_W, screenHeightPx / BG_H)
    private val offsetX: Float = (screenWidthPx - BG_W * scale) / 2f

    /** Hoanh do tren man hinh cua diem `bgPx` trong anh. */
    fun x(bgPx: Float): Dp = with(density) { (offsetX + bgPx * scale).toDp() }

    /** Tung do — `TopCenter` nen khong co do lech doc. */
    fun y(bgPx: Float): Dp = with(density) { (bgPx * scale).toDp() }

    /** Do dai (khong phai toa do) quy doi sang dp. */
    fun len(bgPx: Float): Dp = with(density) { (bgPx * scale).toDp() }
}

/**
 * Chia [count] la bai thanh cac hang.
 *
 * x10 dung bo cuc **2–3–3–2** nhu ban thiet ke (`PreviewGachaScreen_Gachax10.png`):
 * hang 2 la lech nua buoc cot so voi hang 3 la nen nhin ra hinh thoi. Ban thiet ke
 * ve hoi lech trai ~15px; o day tinh lai cho **can giua tuyet doi** (user chot).
 */
internal fun cardRows(count: Int): List<Int> = when {
    count <= 1 -> listOf(count)
    count == 10 -> listOf(2, 3, 3, 2)
    // Phong xa cho so luot quay khac (hien server chi cho 1 va 10)
    else -> buildList {
        var left = count
        while (left > 0) {
            add(minOf(3, left))
            left -= 3
        }
    }
}
