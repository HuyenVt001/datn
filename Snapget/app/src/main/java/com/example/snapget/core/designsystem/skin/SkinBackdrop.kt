package com.example.snapget.core.designsystem.skin

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Nen chung cua TOAN APP theo skin (Skin v2 "Atmosphere" — SKIN_PLAN.md).
 *
 * Dat MOT lan sau `NavHost` (xem `Navigation.kt`). Man nao muon thay nen thi de
 * nen trong suot (`Scaffold(containerColor = Color.Transparent)`); man noi dung
 * (camera, xem/sua anh) giu nen dac `colors.background` va tu che backdrop.
 *
 * Chi 1 bitmap nen duoc decode cho ca app (khong decode lai moi man).
 * Ornament KHONG ve o day (2026-11-16) — chi o popup/sheet/thong bao.
 * Doi skin -> crossfade 400ms rieng lop nen, khong ve lai cay UI.
 *
 * @param animateParticles false = khong ve hat bay (man noi dung dang che nen,
 *   reduced motion, hoac nguoi dung tat "Ambient effects" trong Settings).
 */
@Composable
fun SkinBackdrop(
    animateParticles: Boolean,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = SkinTheme.skin,
        animationSpec = tween(durationMillis = 400),
        modifier = modifier.fillMaxSize(),
        label = "skinBackdrop",
    ) { skin ->
        Box(Modifier.fillMaxSize()) {
            BackdropBase(skin)
            val decor = skin.decor
            if (animateParticles && decor.particle != null) {
                SkinParticles(decor, Modifier.fillMaxSize())
            }
            // (2026-11-16) Ornament KHONG con ve o nen toan man hinh — chi o popup/sheet/
            // thong bao (skinPopupOrnament, SkinSheetDragHandle) theo yeu cau user.
        }
    }
}

/** Gradient -> anh nen + lop toi -> vang sang. */
@Composable
private fun BackdropBase(skin: AppSkin) {
    val atmosphere = skin.atmosphere
    val background = skin.colors.background
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (atmosphere.gradient.size >= 2) {
                    drawRect(Brush.verticalGradient(atmosphere.gradient))
                } else {
                    drawRect(background)
                }
            },
    )
    skin.images.screenBackground?.let { res ->
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Lop toi nhuom mau nen skin (khong phai den tron) -> giu tong mau skin
        Box(Modifier.fillMaxSize().background(background.copy(alpha = atmosphere.imageScrim)))
    }
    if (atmosphere.glow.isSpecified) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(atmosphere.glow, Color.Transparent),
                            center = Offset(size.width * atmosphere.glowCenterX, size.height * atmosphere.glowCenterY),
                            radius = size.maxDimension * 0.6f,
                        ),
                    )
                },
        )
    }
}

// ============================ Hat bay ============================

/** Tham so 1 hat — vi tri la HAM cua thoi gian, khong co state thay doi. */
private class Particle(
    val x0: Float,
    val phase: Float,
    /** Phan quang duong (dinh -> day) di duoc moi giay. */
    val speed: Float,
    val sizeDp: Float,
    val swayDp: Float,
    val swayFreq: Float,
    val rot0: Float,
    val rotSpeed: Float,
    val flipFreq: Float,
    val alpha: Float,
)

private fun Random.between(from: Float, to: Float) = from + nextFloat() * (to - from)

private fun newParticle(rnd: Random, decor: SkinDecor): Particle {
    val drift = decor.particleMotion == ParticleMotion.DRIFT
    val direction = if (rnd.nextBoolean()) 1f else -1f
    return Particle(
        x0 = rnd.nextFloat(),
        phase = rnd.nextFloat(),
        // 1 vong roi het man mat ~12-20s (tuyet) / ~14-22s (la): cham, khong gay roi mat
        speed = if (drift) rnd.between(1f / 22f, 1f / 14f) else rnd.between(1f / 20f, 1f / 12f),
        sizeDp = rnd.between(decor.particleMinDp, decor.particleMaxDp),
        swayDp = if (drift) rnd.between(24f, 48f) else rnd.between(8f, 20f),
        swayFreq = rnd.between(0.08f, 0.22f),
        rot0 = rnd.between(0f, 360f),
        rotSpeed = direction * (if (drift) rnd.between(20f, 60f) else rnd.between(8f, 25f)),
        flipFreq = rnd.between(0.12f, 0.3f),
        alpha = if (drift) rnd.between(0.65f, 0.95f) else rnd.between(0.5f, 0.9f),
    )
}

/**
 * Hat bay ve bang 1 Canvas: doc `time` CHI trong pha ve -> moi frame chi ve lai
 * lop nay (graphicsLayer rieng), KHONG recompose, khong ve lai noi dung man.
 */
@Composable
private fun SkinParticles(decor: SkinDecor, modifier: Modifier) {
    val res = decor.particle ?: return
    val image: ImageBitmap = ImageBitmap.imageResource(res)
    val particles = remember(decor) {
        val rnd = Random(decor.hashCode())
        List(decor.particleCount.coerceIn(0, MAX_PARTICLES)) { newParticle(rnd, decor) }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(decor) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    val drift = decor.particleMotion == ParticleMotion.DRIFT
    val srcSize = IntSize(image.width, image.height)

    Canvas(modifier.graphicsLayer()) {
        val t = time
        particles.forEach { p ->
            val sizePx = p.sizeDp * density
            val half = sizePx / 2f
            val travel = size.height + sizePx * 2f
            val progress = (p.phase + t * p.speed) % 1f
            val y = -sizePx + progress * travel
            val wrapWidth = size.width + sizePx * 2f
            val rawX = p.x0 * size.width +
                (if (drift) progress * size.width * 0.3f else 0f) +
                sin(2f * PI.toFloat() * (p.swayFreq * t + p.phase)) * p.swayDp * density
            val x = ((rawX + sizePx) % wrapWidth + wrapWidth) % wrapWidth - sizePx
            // La: lat qua lat lai (scaleX dao dau) — gia lap xoay 3D re tien
            val flip = if (drift) {
                val c = cos(2f * PI.toFloat() * (p.flipFreq * t + p.phase))
                sign(c).let { s -> (if (s == 0f) 1f else s) * abs(c).coerceAtLeast(0.2f) }
            } else {
                1f
            }
            translate(x, y) {
                rotate(p.rot0 + p.rotSpeed * t, pivot = Offset.Zero) {
                    scale(flip, 1f, pivot = Offset.Zero) {
                        drawImage(
                            image = image,
                            srcSize = srcSize,
                            dstOffset = IntOffset(-half.toInt(), -half.toInt()),
                            dstSize = IntSize(sizePx.toInt(), sizePx.toInt()),
                            alpha = p.alpha,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_PARTICLES = 20

// ============================ Kinh ============================

/** 3 bac kinh: Low 6% (card thuong) · Base 10% · High 14% (noi bat/dang chon). */
enum class GlassLevel(val extraAlpha: Float) {
    Low(0f),
    Base(0.04f),
    High(0.08f),
}

/**
 * Mau nen kinh theo skin — dung cho API can `Color` (containerColor cua Card/Button).
 * Trong suot nen PHAI dat tren backdrop hoac nen dac; dung cho sheet/dialog thi
 * dung `surfaceContainer*` (dac) thay vi ham nay.
 */
@Composable
@ReadOnlyComposable
fun glassColor(level: GlassLevel = GlassLevel.Low): Color {
    val atmosphere = SkinTheme.skin.atmosphere
    return atmosphere.glassTint.copy(alpha = atmosphere.glassAlpha + level.extraAlpha)
}

/**
 * Be mat kinh: nen to mau trong + vien 1px cung tong. Thay cho
 * `.background(colors.surface)` cua card nam tren man co backdrop.
 * Khong clip — caller giu `.clip(shape)` nhu cu neu noi dung can cat goc.
 */
@Composable
fun Modifier.skinGlass(
    shape: Shape = SkinTheme.shapes.card,
    level: GlassLevel = GlassLevel.Low,
): Modifier {
    val atmosphere = SkinTheme.skin.atmosphere
    return this
        .background(glassColor(level), shape)
        .border(1.dp, atmosphere.glassTint.copy(alpha = atmosphere.glassBorderAlpha), shape)
}
