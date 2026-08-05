package com.example.snapget.core.designsystem.effect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.snapget.core.designsystem.skin.SkinTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * So lan phat song toi da cung luc. Cham lien tuc (vuot nhanh) co the sinh rat
 * nhieu lan phat — chan tran de khong tut khung hinh tren may yeu.
 */
private const val MAX_LIVE_EMISSIONS = 8

/** 1 lan cham -> 1 cum hat, tu bien mat khi het vong doi. */
private data class Emission(
    val id: Long,
    val origin: Offset,
    val startMs: Long,
    val seeds: List<Float>,
)

/**
 * Lop phu bat cham toan man va ve hieu ung tai diem cham (SKIN_PLAN.md muc 2.5).
 *
 * Dat BOC NGOAI `NavHost` -> viet 1 lan, moi man deu co.
 *
 * ### Vi sao khong lam vo nut/scroll/pager
 * Dung `PointerEventPass.Initial` va **KHONG goi `consume()`**: chi "nghe lom"
 * su kien roi tha nguyen cho cay ben duoi xu ly. Nho vay nut bam, `VerticalPager`
 * cua feed, giu-de-quay-GIF va pinch-zoom deu nhan du su kien y nhu cu.
 *
 * @param enabled `false` = tam tat (vd dang quay GIF o man camera — user chot
 *   2026-08-05). Tat thi khong dang ky `pointerInput` luon, khong ton gi.
 */
@Composable
fun TouchEffectOverlay(
    effect: TouchEffect,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val active = enabled && effect.id != TouchEffectRegistry.NONE_ID

    if (!active) {
        Box(modifier) { content() }
        return
    }

    val emissions = remember { mutableStateListOf<Emission>() }
    val density = LocalDensity.current
    val accent = SkinTheme.colors.accent
    val color = if (effect.useSkinAccent) accent else Color.White

    // Dong ho chung: 1 vong 10s chay lien tuc. Dung 1 transition cho MOI hat
    // thay vi Animatable moi lan cham — cham 20 phat lien khong sinh 20 coroutine.
    val clock = rememberInfiniteTransition(label = "touch-effect-clock")
    val tick by clock.animateFloat(
        initialValue = 0f,
        targetValue = 10_000f,
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing)),
        label = "tick",
    )

    Box(
        modifier = modifier.pointerInput(effect.id) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (down != null) {
                        if (emissions.size >= MAX_LIVE_EMISSIONS) {
                            emissions.removeAt(0)
                        }
                        emissions.add(
                            Emission(
                                id = down.id.value,
                                origin = down.position,
                                startMs = tick.toLong(),
                                seeds = List(effect.particleCount) { Random.nextFloat() },
                            ),
                        )
                    }
                    // TUYET DOI khong consume(): consume la nut/scroll ben duoi chet
                }
            }
        },
    ) {
        content()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = density.density
            val sizePx = effect.sizeDp * scale
            val distancePx = effect.distanceDp * scale
            val swayPx = effect.swayDp * scale

            emissions.forEach { emission ->
                // `tick` chay vong nen co the nho hon startMs -> cong bu 1 vong
                val raw = tick - emission.startMs
                val elapsed = if (raw < 0) raw + 10_000f else raw
                val progress = elapsed / effect.durationMs
                if (progress > 1f) return@forEach

                emission.seeds.forEachIndexed { index, seed ->
                    drawParticle(
                        effect = effect,
                        color = color,
                        origin = emission.origin,
                        index = index,
                        seed = seed,
                        progress = progress,
                        sizePx = sizePx,
                        distancePx = distancePx,
                        swayPx = swayPx,
                    )
                }
            }
        }
    }

    // Khong can don list: cum het vong doi thi `progress > 1` nen khong ve gi,
    // va [MAX_LIVE_EMISSIONS] da chan tran 8 phan tu. Don o day se la side-effect
    // NGAY TRONG luc compose — Compose cam, va de gay recompose vo han.
}

@Suppress("LongParameterList")
private fun DrawScope.drawParticle(
    effect: TouchEffect,
    color: Color,
    origin: Offset,
    index: Int,
    seed: Float,
    progress: Float,
    sizePx: Float,
    distancePx: Float,
    swayPx: Float,
) {
    // Goc phat: chia deu quanh vong tron roi lech nhe theo seed -> khong bi deu
    // tam tap nhu hinh sao 8 canh
    val baseAngle = (index.toFloat() / effect.particleCount) * 2f * PI.toFloat()
    val angle = baseAngle + (seed - 0.5f) * 0.6f
    val travel = distancePx * (0.7f + seed * 0.6f)

    val offset = when (effect.direction) {
        EmitDirection.RADIAL -> Offset(
            cos(angle) * travel * progress,
            sin(angle) * travel * progress,
        )

        EmitDirection.FALL_SWAY -> Offset(
            sin(progress * PI.toFloat() * 2f + seed * 6f) * swayPx,
            travel * progress,
        )

        EmitDirection.RISE_SWAY -> Offset(
            sin(progress * PI.toFloat() * 2f + seed * 6f) * swayPx,
            -travel * progress,
        )

        // Ban len roi roi: parabol y = -4h·t(1-t) cho dinh o giua vong doi
        EmitDirection.BURST_FALL -> Offset(
            cos(angle) * travel * progress * 0.6f,
            -4f * travel * progress * (1f - progress) + travel * progress * 0.5f,
        )
    }

    val scale = effect.scaleFrom + (effect.scaleTo - effect.scaleFrom) * progress
    val alpha = if (progress < effect.fadeStart) {
        1f
    } else {
        1f - (progress - effect.fadeStart) / (1f - effect.fadeStart)
    }
    if (alpha <= 0f) return

    val center = origin + offset
    val radius = sizePx * scale / 2f
    val spin = effect.spinDegPerSec * (progress * effect.durationMs / 1000f) + seed * 360f

    rotate(degrees = spin, pivot = center) {
        when (effect.shape) {
            ParticleShape.CIRCLE -> drawCircle(color, radius, center, alpha)

            ParticleShape.RING -> {
                drawCircle(color, radius, center, alpha * 0.7f, Stroke(width = radius * 0.22f))
                // Cham sang lech tam — cho ra chat "bong bong" thay vi vong tron tron
                drawCircle(
                    color,
                    radius * 0.18f,
                    center + Offset(-radius * 0.35f, -radius * 0.35f),
                    alpha,
                )
            }

            ParticleShape.SPARK -> {
                val arm = radius
                val thin = radius * 0.16f
                drawPath(sparkPath(center, arm, thin), color, alpha)
            }

            ParticleShape.LEAF -> drawPath(leafPath(center, radius), color, alpha)
        }
    }
}

/** Tia 4 canh: 2 hinh thoi long nhau. */
private fun sparkPath(center: Offset, arm: Float, thin: Float): Path = Path().apply {
    moveTo(center.x, center.y - arm)
    lineTo(center.x + thin, center.y)
    lineTo(center.x, center.y + arm)
    lineTo(center.x - thin, center.y)
    close()
    moveTo(center.x - arm, center.y)
    lineTo(center.x, center.y - thin)
    lineTo(center.x + arm, center.y)
    lineTo(center.x, center.y + thin)
    close()
}

/** Hinh la: 2 duong cong doi xung gap nhau o 2 dau. */
private fun leafPath(center: Offset, radius: Float): Path = Path().apply {
    val top = Offset(center.x, center.y - radius)
    val bottom = Offset(center.x, center.y + radius)
    moveTo(top.x, top.y)
    quadraticTo(center.x + radius, center.y, bottom.x, bottom.y)
    quadraticTo(center.x - radius, center.y, top.x, top.y)
    close()
}

/** Ve hat len Canvas co san (dung cho o demo trong tab Effects). */
internal fun DrawScope.drawEmission(
    effect: TouchEffect,
    color: Color,
    origin: Offset,
    progress: Float,
    seeds: List<Float>,
    density: Float,
) {
    seeds.forEachIndexed { index, seed ->
        drawParticle(
            effect = effect,
            color = color,
            origin = origin,
            index = index,
            seed = seed,
            progress = progress,
            sizePx = effect.sizeDp * density,
            distancePx = effect.distanceDp * density,
            swayPx = effect.swayDp * density,
        )
    }
}

/** Kich thuoc mac dinh cua o demo (tab Effects ve o giua o vuong). */
internal fun Size.center(): Offset = Offset(width / 2f, height / 2f)
