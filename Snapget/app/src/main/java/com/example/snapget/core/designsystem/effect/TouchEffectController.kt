package com.example.snapget.core.designsystem.effect

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cong tat TAM THOI cua hieu ung cham (SKIN_PLAN.md muc 2.5.4).
 *
 * Khac voi viec chon hieu ung "None" trong man Appearance (nguoi dung tu tat
 * han): day la tat theo NGU CANH, do chinh man hinh dang mo quyet dinh, va tu
 * nha ra khi roi man.
 *
 * Cho duy nhat dung toi hien nay: **dang quay "anh GIF" o man camera** — chot
 * 2026-08-05, dung tinh than `DESIGN.md` 7.5 "khi quay KHONG hien gi them".
 *
 * Dung `staticCompositionLocalOf`: gia tri (doi tuong controller) khong bao gio
 * doi, chi `suppressed` ben trong doi — nen khong can Compose theo doi chinh
 * CompositionLocal.
 */
class TouchEffectController {
    /** true = tam ngung ve hieu ung. [TouchEffectOverlay] doc truc tiep. */
    val suppressed: MutableState<Boolean> = mutableStateOf(false)
}

val LocalTouchEffectController = staticCompositionLocalOf { TouchEffectController() }
