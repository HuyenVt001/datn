package com.example.snapget.core.designsystem.effect

import com.example.snapget.R

/**
 * Danh sach hieu ung touch dong goi trong APK. Server chi giu ID
 * (`users.unlockedEffects[]`) — day la nguon su that duy nhat ve "hieu ung chay
 * nhu the nao".
 *
 * ### Them 1 hieu ung moi (toi da id 10 — user chot 2026-08-11)
 * 1. Bo sheet vao `res/drawable-nodpi/effectN_sheet.png` (PNG-32 alpha, luoi deu,
 *    frame vuong, khong padding/trim).
 * 2. Them 1 `val` o day + nhoi vao [all]; `frameCount`/`columns` phai khop luoi
 *    that cua anh, `playbackMs` khong duoc vuot `durationMs`.
 * 3. Them item EFFECT `refId = "N"` vao `server/scripts/seed-gacha.ts` roi chay
 *    lai script — thieu buoc nay thi **quay gacha khong bao gio ra** hieu ung moi.
 */
object TouchEffectRegistry {

    /** Hieu ung "khong co gi" — luon so huu, mac dinh, khong quay gacha ra duoc. */
    const val NONE_ID = 0

    val None = TouchEffect(
        id = NONE_ID,
        displayName = "None",
    )

    /**
     * Hoa no tu diem cham (sheet 768×384 = 4 cot × 2 hang, frame 192×192).
     *
     * `fadeStart = 0.6f` vi sheet **khong tu tan**: frame 7 la frame dac nhat
     * (hoa no het co). Frame chay xong o moc 444/800ms, giu nguyen mot chut roi
     * mo dan tu moc 480ms den het.
     */
    val Flower = TouchEffect(
        id = 1,
        displayName = "Flower",
        sheet = R.drawable.effect1_sheet,
        frameCount = 8,
        columns = 4,
        fps = 18,
        durationMs = 800,
        sizeDp = 120f,
        fadeStart = 0.6f,
        thumbFrame = 7,
    )

    /*
     * ⚠️ Sheet 2/3/4 (bo them 2026-08-11) deu la anh 768×384 = luoi 4×2, nhung
     * **o thu 8 DE TRONG** (alpha = 0 tuyet doi) -> khai `frameCount = 7`, khong
     * phai 8. Khai 8 thi 1/8 chang cuoi ve mot frame rong: hieu ung chop tat dot
     * ngot roi con dung do het `durationMs`, va `fadeStart` mo dan mot frame
     * khong co gi.
     *
     * Ca 3 sheet deu tu loang o frame 6 (so diem anh duc tut so voi frame 5) roi
     * cat phut sang rong -> giu frame 6 lam frame cuoi va cho `fadeStart` tan not
     * phan con lai la muot.
     */

    /** Chum cau trong mo toa ra tu diem cham. */
    val Snowflake = TouchEffect(
        id = 2,
        displayName = "Snowflake",
        sheet = R.drawable.effect2_sheet,
        frameCount = 7,
        columns = 4,
        fps = 16,
        durationMs = 640,
        sizeDp = 120f,
        fadeStart = 0.60f,
        thumbFrame = 5,
    )

    /** La thu toa ra roi tan. */
    val Leaf = TouchEffect(
        id = 3,
        displayName = "Leaf",
        sheet = R.drawable.effect3_sheet,
        frameCount = 7,
        columns = 4,
        fps = 16,
        durationMs = 660,
        sizeDp = 120f,
        fadeStart = 0.58f,
        thumbFrame = 4,
    )

    /** Vong phep tim hien ra roi mo di — nhanh hon 2 hieu ung tren cho ra chat "niem chu". */
    val Magic = TouchEffect(
        id = 4,
        displayName = "Magic",
        sheet = R.drawable.effect4_sheet,
        frameCount = 7,
        columns = 4,
        fps = 20,
        durationMs = 560,
        sizeDp = 120f,
        fadeStart = 0.55f,
        thumbFrame = 5,
    )

    /** Thu tu trong list = thu tu hien o tab Effects; None luon dung dau. */
    val all: List<TouchEffect> = listOf(None, Flower, Snowflake, Leaf, Magic)

    /**
     * Tim hieu ung theo id, KHONG BAO GIO nem loi — ban app cu co the nhan id
     * cua vat pham chi co o ban moi hon (SKIN_PLAN.md muc 5).
     */
    fun find(id: Int): TouchEffect = all.firstOrNull { it.id == id } ?: None
}
