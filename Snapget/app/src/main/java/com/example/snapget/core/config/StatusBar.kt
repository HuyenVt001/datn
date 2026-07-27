package com.example.snapget.core.config

import android.view.View
import android.view.Window
import android.view.WindowManager

fun statusBarConfig(window: Window) {
    // FLAG_LAYOUT_NO_LIMITS DA XOA (fix 2026-07-27): flag nay lam window bo qua
    // MOI insets -> WindowInsets.ime luon = 0 -> imePadding() vo dung, ban phim
    // CHE o nhap tin nhan. Ve full-man-hinh da co enableEdgeToEdge() lo
    // (edgeToEdgeWithStyle trong MainActivity), hinh anh khong doi.
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
}
