package com.example.snapget.core.designsystem.component.lottie

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.snapget.R
import com.example.snapget.core.designsystem.motion.rememberReducedMotion

/**
 * Bo animation Lottie cua app — file `res/raw/lottie_*.json` (UI_UPGRADE_PLAN.md).
 * Thay animation = chep de dung ten file, khong phai sua code.
 */
enum class SnapAnim(@RawRes val res: Int) {
    STREAK_FIRE(R.raw.lottie_streak_fire),
    CONFETTI(R.raw.lottie_confetti),
    TROPHY(R.raw.lottie_trophy),
    SUCCESS_CHECK(R.raw.lottie_success_check),
    SPARKLE(R.raw.lottie_sparkle),
    GIFT_OPEN(R.raw.lottie_gift_open),
    FRIENDS(R.raw.lottie_friends),
    EMPTY(R.raw.lottie_empty),
    NO_INTERNET(R.raw.lottie_no_internet),
    LOADING(R.raw.lottie_loading),
}

/**
 * Phat 1 animation Lottie.
 * @param loop true = lap vo han (empty/loading/lua streak), false = chay 1 lan (an mung).
 * Reduced motion: khong chay, hien thang frame cuoi (hoac giua neu loop) — van
 * truyen dat duoc y nghia ma khong chuyen dong.
 */
@Composable
fun SnapLottie(
    anim: SnapAnim,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    isPlaying: Boolean = true,
    speed: Float = 1f,
    contentScale: ContentScale = ContentScale.Fit,
    onFinished: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(anim.res))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        isPlaying = isPlaying && !reduced,
        speed = speed,
    )
    if (onFinished != null && !loop && (progress >= 1f || reduced) && composition != null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onFinished() }
    }
    LottieAnimation(
        composition = composition,
        progress = { if (reduced) (if (loop) 0.5f else 1f) else progress },
        modifier = modifier,
        contentScale = contentScale,
    )
}
