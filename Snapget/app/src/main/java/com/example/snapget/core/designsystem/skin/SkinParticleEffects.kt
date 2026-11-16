package com.example.snapget.core.designsystem.skin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.motion.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Ve 1 particle (anh skin) tam o (x, y), xoay/lat/co, giu nguyen mau goc. */
private fun DrawScope.drawParticle(
    image: ImageBitmap,
    x: Float,
    y: Float,
    sizePx: Float,
    rotation: Float = 0f,
    flipX: Float = 1f,
    alpha: Float = 1f,
) {
    val half = sizePx / 2f
    translate(x, y) {
        rotate(rotation, pivot = Offset.Zero) {
            scale(flipX, 1f, pivot = Offset.Zero) {
                drawImage(
                    image = image,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(-half.toInt(), -half.toInt()),
                    dstSize = IntSize(sizePx.toInt(), sizePx.toInt()),
                    alpha = alpha.coerceIn(0f, 1f),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

// ============================ Loading ============================

/**
 * Vong loading theo skin (2026-11-16): particle cua skin XOAY + nhip nhe o giua,
 * 3 hat nho bay vong quanh. FALL (tuyet) xoay deu; DRIFT (la) dua qua lai nhu la roi.
 *
 * Dung cho loading CAP MAN / KHOI (giua man, giua card). Spinner nho trong nut
 * (16-24dp) giu `CircularProgressIndicator` — o co do vong xoay doc ro hon.
 * Skin khong co particle (Default) -> `CircularProgressIndicator` mau [fallbackColor].
 * Reduced motion -> particle dung yen (van bao "dang tai" qua semantics).
 */
@Composable
fun SkinLoading(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    fallbackColor: Color = SkinTheme.colors.accent,
) {
    val decor = SkinTheme.skin.decor
    val res = decor.particle
    if (res == null) {
        CircularProgressIndicator(modifier = modifier, color = fallbackColor)
        return
    }
    val image = ImageBitmap.imageResource(res)
    val reduced = rememberReducedMotion()
    val drift = decor.particleMotion == ParticleMotion.DRIFT
    val transition = rememberInfiniteTransition(label = "skinLoading")
    // 0..1 lap lai: 1 vong quay/quy dao
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "cycle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Loading" },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val c = if (reduced) 0f else cycle
            val p = if (reduced) 1f else pulse
            val center = this.size.width / 2f
            val main = this.size.minDimension * 0.62f * p
            val rotation = if (drift) sin(2f * PI.toFloat() * c) * 35f else c * 360f
            drawParticle(image, center, center, main, rotation)
            // 3 hat ve tinh, lech pha 120°, nho dan theo thu tu
            val radius = this.size.minDimension * 0.44f
            repeat(3) { i ->
                val angle = 2f * PI.toFloat() * (c + i / 3f)
                drawParticle(
                    image = image,
                    x = center + cos(angle) * radius,
                    y = center + sin(angle) * radius,
                    sizePx = this.size.minDimension * (0.2f - i * 0.03f),
                    rotation = -c * 360f,
                    alpha = 0.85f - i * 0.2f,
                )
            }
        }
    }
}

// ============================ An mung ============================

private class BurstPiece(
    val angle: Float,
    /** Quang duong bay theo ti le kich thuoc vung ve. */
    val reach: Float,
    val sizeDp: Float,
    val rot0: Float,
    val rotSpeed: Float,
    val flipFreq: Float,
)

/**
 * Hat cua skin NO TOA RA tu [originX]/[originY] (ti le vung ve), bay cham dan +
 * roi theo trong luc + mo dan — CHAY 1 LAN moi khi [trigger] doi (2026-11-16).
 * Dung o khoanh khac an mung: streak, moc streak, ket ban thanh cong, mo ruong
 * quest, mua do o Shop.
 *
 * Skin khong co particle (Default) hoac reduced motion -> khong ve gi; noi goi giu
 * Lottie confetti/sparkle cu cho truong hop do (xem [hasSkinParticle]).
 *
 * @param trigger doi gia tri = phat lai. `null` = khong phat.
 */
@Composable
fun SkinParticleBurst(
    trigger: Any?,
    modifier: Modifier = Modifier,
    count: Int = 24,
    durationMs: Int = 1500,
    originX: Float = 0.5f,
    originY: Float = 0.45f,
) {
    val decor = SkinTheme.skin.decor
    val res = decor.particle ?: return
    if (trigger == null || rememberReducedMotion()) return
    val image = ImageBitmap.imageResource(res)
    val drift = decor.particleMotion == ParticleMotion.DRIFT
    key(trigger) {
        val progress = remember { Animatable(0f) }
        val pieces = remember {
            val rnd = Random(trigger.hashCode())
            List(count.coerceIn(1, 40)) {
                BurstPiece(
                    // Rai deu quanh vong tron + lech ngau nhien -> khong thanh "nan hoa"
                    angle = (it.toFloat() / count + rnd.nextFloat() * 0.04f) * 2f * PI.toFloat(),
                    reach = 0.12f + rnd.nextFloat() * 0.46f, // rong -> khong thanh "vong tron"
                    sizeDp = decor.particleMinDp + 6f + rnd.nextFloat() * (decor.particleMaxDp - decor.particleMinDp + 6f),
                    rot0 = rnd.nextFloat() * 360f,
                    rotSpeed = (if (rnd.nextBoolean()) 1f else -1f) * (180f + rnd.nextFloat() * 360f),
                    flipFreq = 1.5f + rnd.nextFloat() * 2f,
                )
            }
        }
        LaunchedEffect(Unit) {
            progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        }
        Canvas(modifier.fillMaxSize().graphicsLayer()) {
            val t = progress.value
            if (t >= 1f) return@Canvas
            val ox = size.width * originX
            val oy = size.height * originY
            val span = size.maxDimension
            // La roi cham hon tuyet (can gio lon)
            val gravity = span * if (drift) 0.18f else 0.3f
            val eased = 1f - (1f - t).pow(3) // bay nhanh luc dau, cham dan
            val alpha = 1f - t.pow(2.2f)
            pieces.forEach { p ->
                val dist = p.reach * span * eased
                val flip = if (drift) cos(2f * PI.toFloat() * p.flipFreq * t).let { if (it >= 0f) it.coerceAtLeast(0.2f) else it.coerceAtMost(-0.2f) } else 1f
                drawParticle(
                    image = image,
                    x = ox + cos(p.angle) * dist,
                    y = oy + sin(p.angle) * dist + gravity * t * t,
                    sizePx = p.sizeDp.dp.toPx() * (0.6f + 0.4f * eased),
                    rotation = p.rot0 + p.rotSpeed * t,
                    flipX = flip,
                    alpha = alpha,
                )
            }
        }
    }
}

/** true khi skin co particle VA khong reduced motion -> noi goi thay Lottie confetti bang [SkinParticleBurst]. */
val hasSkinParticle: Boolean
    @Composable
    get() = SkinTheme.skin.decor.particle != null && !rememberReducedMotion()
