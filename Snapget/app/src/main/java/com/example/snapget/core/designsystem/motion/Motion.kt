package com.example.snapget.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Token chuyen dong "Playful Dark" — DESIGN.md muc 0.2.
 * Chi animate graphicsLayer (scale/alpha/translation), khong animate kich thuoc.
 */
object MotionTokens {
    /** Nhan nut: co lai nhanh, nha ra nay nhe. */
    val press = spring<Float>(dampingRatio = 0.5f, stiffness = 800f)

    /** "Pop": badge moi, emoji, item vua mua, check quest — vuot 1.08 roi ve 1. */
    val pop = spring<Float>(dampingRatio = 0.45f, stiffness = 400f)

    /** Nay vua cho tab/icon doi trang thai. */
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    const val ENTER_MS = 300
    const val EXIT_MS = 150 // exit NHANH hon enter
    const val STAGGER_MS = 40
    const val STAGGER_MAX_ITEMS = 8

    val enter = tween<Float>(ENTER_MS, easing = FastOutSlowInEasing)
    val exit = tween<Float>(EXIT_MS)

    /** Scale khi nhan (0.95 — DESIGN.md 0.1). */
    const val PRESSED_SCALE = 0.95f
}

/**
 * true khi nguoi dung tat animation he thong ("Remove animations" /
 * Animator duration scale = 0). Khi do: bo confetti/stagger, giu fade ngan.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Co phan tu lai khi dang nhan (theo [interactionSource] cua clickable ben duoi).
 * Dung chung voi `clickable(interactionSource = ...)` de khong mat ripple/semantics.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = MotionTokens.PRESSED_SCALE,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = MotionTokens.press,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * "Pop" moi lan [key] doi (vd so luong, trang thai completed): 0.6 -> 1.08 -> 1.
 * Bo qua lan dau (khong pop khi man vua mo) va khi reduced motion.
 */
fun Modifier.popOnChange(key: Any?): Modifier = composed {
    val reduced = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (!reduced) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, MotionTokens.pop)
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Hien dan cho item thu [index] trong list (stagger 40ms, toi da 8 item) —
 * fade + truot len 16dp. Item sau muc 8 hien ngay khong delay.
 */
fun Modifier.staggerEnter(index: Int): Modifier = composed {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            val delayMs = index.coerceAtMost(MotionTokens.STAGGER_MAX_ITEMS) * MotionTokens.STAGGER_MS
            delay(delayMs.toLong())
            progress.animateTo(1f, MotionTokens.enter)
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}
