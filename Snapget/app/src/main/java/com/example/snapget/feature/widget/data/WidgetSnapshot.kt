package com.example.snapget.feature.widget.data

/** Trang thai widget quyet dinh boi worker (khong goi mang luc render). */
enum class WidgetStateKind {
    SIGNED_OUT,
    EMPTY,
    OK,
}

/**
 * Snapshot du lieu widget luu local (SharedPreferences + file anh) —
 * GlanceAppWidget CHI doc snapshot nay, khong bao gio goi mang truc tiep.
 */
data class WidgetSnapshot(
    val kind: WidgetStateKind,
    val streak: Int = 0,
    val momentId: String? = null,
    val imagePath: String? = null,
    val updatedAt: Long = 0L,
)
