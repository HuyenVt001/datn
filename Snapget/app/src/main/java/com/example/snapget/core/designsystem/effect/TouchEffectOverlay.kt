package com.example.snapget.core.designsystem.effect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.snapget.core.designsystem.skin.SkinTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * So lan phat song toi da cung luc. Cham lien tuc (vuot nhanh) co the sinh rat
 * nhieu lan phat — chan tran de khong tut khung hinh tren may yeu.
 */
private const val MAX_LIVE_EMISSIONS = 8

/**
 * 1 lan cham -> 1 cum hat, tu bien mat khi het vong doi.
 *
 * KHONG phai `data class`: [seeds] la mang, `equals`/`hashCode` sinh tu dong se
 * so sanh theo tham chieu — vo nghia va gay hieu nham.
 */
private class Emission(
    val origin: Offset,
    /**
     * Moc bat dau, don vi ms, lay tu **dong ho don dieu khong lap vong**
     * (`System.nanoTime`). Truoc day dung dong ho animation chay vong 10s nen
     * cum cham cu **phat lai** moi 10 giay khi man hinh de yen — xem [nowMs].
     */
    val startMs: Long,
    /** `FloatArray` chu khong `List<Float>`: khong boxing khi doc lai moi frame. */
    val seeds: FloatArray,
)

/** Moc thoi gian don dieu, CUNG goc voi `withFrameMillis` tren Android. */
private fun nowMs(): Long = System.nanoTime() / 1_000_000L

/**
 * Anh hat + bo loc mau da dung san.
 *
 * Tinh MOT LAN o composition roi dung lai cho moi frame: `ColorFilter.tint()`
 * cap phat doi tuong moi, goi trong vong lap ve se sinh hang nghin object/giay.
 */
@Immutable
internal class ParticleStyle(
    val image: ImageBitmap?,
    val tint: ColorFilter?,
    /** Mau ve hinh tron du phong khi hieu ung chua co [TouchEffect.particleAsset]. */
    val color: Color,
)

/** Nap anh hat (co cache theo resource id) + dung bo loc mau theo skin dang dung. */
@Composable
internal fun rememberParticleStyle(effect: TouchEffect): ParticleStyle {
    val color = if (effect.useSkinAccent) SkinTheme.colors.accent else Color.White
    val asset = effect.particleAsset
    val image = if (asset == null) null else ImageBitmap.imageResource(asset)
    return remember(image, color, effect.useSkinAccent) {
        ParticleStyle(
            image = image,
            tint = if (effect.useSkinAccent) ColorFilter.tint(color) else null,
            color = color,
        )
    }
}

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
 * @param enabled `false` = tat cung. Ngoai ra con tat MEM theo ngu canh qua
 *   [LocalTouchEffectController] — man camera bat co do trong luc quay GIF.
 *   Tat thi khong ve Canvas va bo qua su kien cham, khong ton gi.
 */
@Composable
fun TouchEffectOverlay(
    effect: TouchEffect,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Doc co "tam ngung" NGAY TAI DAY chu khong o MainActivity: chi rieng overlay
    // recompose khi bat/tat quay GIF, khong keo ca cay UI recompose theo.
    val suppressed = LocalTouchEffectController.current.suppressed.value
    val active = enabled &&
        !suppressed &&
        effect.id != TouchEffectRegistry.NONE_ID &&
        effect.particleCount > 0

    val emissions = remember { mutableStateListOf<Emission>() }
    // Tang moi lan cham — dung lam khoa khoi dong lai dong ho. KHONG dung
    // `emissions.size` vi khi da cham tran 8 thi size dung yen, dong ho se khong
    // duoc gia han va cum dang bay bi xoa giua chung.
    var emissionSeq by remember { mutableIntStateOf(0) }
    var frameMs by remember { mutableLongStateOf(0L) }

    val style = rememberParticleStyle(effect)
    val density = LocalDensity.current.density

    /*
     * Doc trong `pointerInput` ma KHONG lam key doi -> bo bat su kien KHONG bi
     * huy/tao lai moi lan doi hieu ung. Truoc day key la `effect.id`: bam nhanh
     * qua nhieu o trong tab Effects lam detector bi dung lai lien tuc, cham
     * dang do bi nuot -> cam giac "do, giat".
     */
    val liveEffect by rememberUpdatedState(effect)
    val liveActive by rememberUpdatedState(active)

    /*
     * Doi hieu ung (hoac tat hieu ung) -> bo cum dang bay.
     *
     * Khong bo thi cum sinh boi hieu ung cu se duoc ve tiep bang ANH + tham so
     * cua hieu ung moi — bam nhanh qua vai o la thay hat doi hinh giua chung.
     */
    LaunchedEffect(active, effect.id) { emissions.clear() }

    /*
     * Dong ho CHI chay khi con cum dang bay (truoc day la `rememberInfiniteTransition`
     * chay suot vong doi app — ve lai moi frame ke ca khi khong co gi de ve).
     * Het cum thi vong lap thoat, khong ton frame nao nua.
     *
     * ⚠️ `lastOrNull()` chu khong `last()`: `withFrameMillis` la diem treo, trong
     * luc cho frame thi `LaunchedEffect(active, effect.id)` o tren co the da
     * `clear()` danh sach -> `last()` nem `NoSuchElementException` lam **sap app**.
     * Dung la kich ban "bam lien tuc vao tab Effects roi bam None".
     */
    LaunchedEffect(emissionSeq) {
        while (emissions.isNotEmpty()) {
            withFrameMillis { frameMs = it }
            val newest = emissions.lastOrNull() ?: break
            if (frameMs - newest.startMs > effect.durationMs) {
                emissions.clear()
            }
        }
    }

    /*
     * ⚠️ `content()` phai nam o DUNG MOT vi tri goi, du dang bat hay tat hieu ung.
     * Truoc day co nhanh `if (!active) { Box { content() }; return }` rieng: bat/tat
     * hieu ung lam Compose huy va dung lai ca cay ben duoi -> `rememberNavController`
     * trong `Navigation()` sinh lai -> nguoi dung bi nem ve man hinh dau tien ngay
     * khi vua chon hieu ung trong man Appearance.
     *
     * Cung ly do: `pointerInput` gan CO DINH (key `Unit`), khong nhanh bat/tat theo
     * `active` — no chi "nghe lom" nen khi tat thi bo qua su kien, khong ton gi.
     */
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!liveActive) continue
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                        ?: continue
                    val current = liveEffect
                    if (emissions.size >= MAX_LIVE_EMISSIONS) {
                        emissions.removeAt(0)
                    }
                    emissions.add(
                        Emission(
                            origin = down.position,
                            startMs = nowMs(),
                            seeds = FloatArray(current.particleCount) { Random.nextFloat() },
                        ),
                    )
                    emissionSeq++
                    // TUYET DOI khong consume(): consume la nut/scroll ben duoi chet
                }
            }
        },
    ) {
        content()

        if (active) {
            /*
             * `graphicsLayer()` = Canvas nay co RenderNode RIENG. Khong co no,
             * moi frame hat bay se lam ban vung ve cua CA cay UI ben duoi (feed
             * anh, camera preview...) va Compose phai ghi lai toan bo display
             * list -> chinh la doan "lag" khi hat dang bay.
             */
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val sizePx = effect.sizeDp * density
                val distancePx = effect.distanceDp * density
                val swayPx = effect.swayDp * density
                val durationMs = effect.durationMs.coerceAtLeast(1)

                emissions.forEach { emission ->
                    val progress = (frameMs - emission.startMs).toFloat() / durationMs
                    // progress < 0: frame dau tien sau khi cham, dong ho chua kip
                    // tick lan nao -> bo qua 1 frame thay vi ve o vi tri sai.
                    if (progress < 0f || progress > 1f) return@forEach

                    for (index in emission.seeds.indices) {
                        drawParticle(
                            effect = effect,
                            style = style,
                            origin = emission.origin,
                            index = index,
                            total = emission.seeds.size,
                            seed = emission.seeds[index],
                            progress = progress,
                            sizePx = sizePx,
                            distancePx = distancePx,
                            swayPx = swayPx,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ve 1 hat.
 *
 * Tu 2026-08-06 ve **anh that** (`effect.particleAsset`) thay vi hinh hoc tu ve:
 * bong tuyet 6 canh co nhanh, la co gan, sao 8 canh... dung y anh trong
 * `Sources/skin-assets/effects/`. Ngoai chuyen giong thiet ke, cach nay con
 * NHANH HON HAN: ban cu cap phat 1 `Path` moi cho MOI hat MOI frame
 * (~80 object/frame = ~4.800 object/giay) nen GC chay lien tuc gay giat.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawParticle(
    effect: TouchEffect,
    style: ParticleStyle,
    origin: Offset,
    index: Int,
    total: Int,
    seed: Float,
    progress: Float,
    sizePx: Float,
    distancePx: Float,
    swayPx: Float,
) {
    // Goc phat: chia deu quanh vong tron roi lech nhe theo seed -> khong bi deu
    // tam tap nhu hinh sao 8 canh
    val baseAngle = (index.toFloat() / total) * 2f * PI.toFloat()
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
    // coerce: `fadeStart = 1f` se chia cho 0 -> alpha NaN/-Infinity
    val fadeSpan = (1f - effect.fadeStart).coerceAtLeast(0.001f)
    val alpha = if (progress < effect.fadeStart) {
        1f
    } else {
        1f - (progress - effect.fadeStart) / fadeSpan
    }
    if (alpha <= 0f) return

    val center = origin + offset
    val side = sizePx * scale
    // Duoi 1px thi ve cung khong ai thay, bo qua cho re
    if (side < 1f) return

    val image = style.image
    if (image == null) {
        drawCircle(style.color, side / 2f, center, alpha)
        return
    }

    val sidePx = side.roundToInt()
    val dstSize = IntSize(sidePx, sidePx)
    val dstOffset = IntOffset(
        (center.x - side / 2f).roundToInt(),
        (center.y - side / 2f).roundToInt(),
    )

    // Khong xoay (vd Bubble) -> bo luon `rotate`, tiet kiem 1 cap save/restore moi hat
    if (effect.spinDegPerSec == 0f) {
        drawImage(image, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha, colorFilter = style.tint)
        return
    }

    val spin = effect.spinDegPerSec * (progress * effect.durationMs / 1000f) + seed * 360f
    rotate(degrees = spin, pivot = center) {
        drawImage(image, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha, colorFilter = style.tint)
    }
}

/** Ve hat len Canvas co san (dung cho o demo trong tab Effects). */
internal fun DrawScope.drawEmission(
    effect: TouchEffect,
    style: ParticleStyle,
    origin: Offset,
    progress: Float,
    seeds: FloatArray,
    density: Float,
) {
    for (index in seeds.indices) {
        drawParticle(
            effect = effect,
            style = style,
            origin = origin,
            index = index,
            total = seeds.size,
            seed = seeds[index],
            progress = progress,
            sizePx = effect.sizeDp * density,
            distancePx = effect.distanceDp * density,
            swayPx = effect.swayDp * density,
        )
    }
}
