package com.example.snapget.core.designsystem.feedback

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Phan hoi XUC GIAC (rung) theo bang DESIGN.md muc 0.4.
 * Rung qua `View.performHapticFeedback` -> ton trong cai dat rung cua he thong.
 *
 * (2026-11-15) App KHONG co am thanh — user chot bo hoan toan (da xoa SoundEffects,
 * res/raw/sfx_*, setting "Sound effects"). Chi con rung.
 *
 * Dung: `val feedback = rememberFeedback()` roi `feedback.tap()` / `.success()`...
 */
@Stable
class Feedback internal constructor(private val view: View) {
    /** Nhan nut thuong, tha reaction, gui tin — rung rat nhe. */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Chup anh / bat dau quay GIF. */
    fun shutter() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Thanh cong nho: xong quest, mua item, ket ban. */
    fun success() = confirmHaptic()

    /** Khoanh khac lon: streak +1 / moc streak. */
    fun celebrate() = confirmHaptic()

    /** Pop nho (emoji, badge). */
    fun pop() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Loi (di kem Motion shake). */
    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }

    private fun confirmHaptic() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberFeedback(): Feedback {
    val view = LocalView.current
    return remember(view) { Feedback(view) }
}
