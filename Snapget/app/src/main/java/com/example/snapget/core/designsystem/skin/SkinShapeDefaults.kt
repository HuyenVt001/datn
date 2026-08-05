package com.example.snapget.core.designsystem.skin

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Bo goc mac dinh — dung CHINH XAC gia tri dang chay trong app (DESIGN.md),
 * de doi sang token khong lam xe dich giao dien hien tai mot pixel nao.
 */
object SkinShapeDefaults {
    /** `RoundedCornerShape(50)` — pill/chip/nut tron. */
    val Pill: Shape = RoundedCornerShape(50)

    /** `RoundedCornerShape(20.dp)` — the/khoi noi dung. */
    val Card: Shape = RoundedCornerShape(20.dp)

    /** `RoundedCornerShape(20.dp)` — anh moment/khung. */
    val Image: Shape = RoundedCornerShape(20.dp)

    /** `RoundedCornerShape(24.dp)` — bottom sheet, bong bong tin nhan. */
    val Sheet: Shape = RoundedCornerShape(24.dp)

    /** `RoundedCornerShape(16.dp)` — o nhap lieu. */
    val Input: Shape = RoundedCornerShape(16.dp)
}
