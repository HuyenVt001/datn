package com.example.snapget.core.designsystem.skin

import com.example.snapget.core.designsystem.skin.skins.DefaultSkin
import com.example.snapget.core.designsystem.skin.skins.ForestSkin
import com.example.snapget.core.designsystem.skin.skins.SnowSkin

/**
 * Danh sach skin dong goi trong APK. Server chi giu ID (`users.unlockedSkins[]`)
 * nen day la nguon su that duy nhat ve "skin trong thuc te nhu the nao".
 *
 * Them skin moi: viet file trong `skins/`, them vao [all] — khong phai sua gi
 * o server.
 */
object SkinRegistry {

    /** Thu tu trong list = thu tu hien o tab Skins; Default luon dung dau. */
    val all: List<AppSkin> = listOf(
        DefaultSkin,
        SnowSkin,
        ForestSkin,
    )

    /** ID cua skin mac dinh — luon so huu, khong quay gacha ra duoc. */
    const val DEFAULT_ID = 0

    /**
     * Tim skin theo id, KHONG BAO GIO nem loi.
     *
     * Ban app cu co the nhan id cua vat pham chi co o ban moi hon (server tra ve
     * `unlockedSkins` gom ca id la) — luc do roi ve [DefaultSkin] thay vi crash.
     */
    fun find(id: Int): AppSkin = all.firstOrNull { it.id == id } ?: DefaultSkin
}
