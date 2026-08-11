package com.example.snapget.core.designsystem.effect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * So animation toi da chay cung luc. Cham lien tuc (vuot nhanh) co the sinh rat
 * nhieu lan phat — chan tran de khong tut khung hinh tren may yeu.
 */
private const val MAX_LIVE_EMISSIONS = 8

/**
 * 1 lan cham -> 1 animation, tu bien mat khi het vong doi.
 *
 * Tu 2026-08-11 khong con mang `seeds`: hieu ung la spritesheet one-shot nen
 * khong co hat nao can gieo ngau nhien, chuyen dong nam san trong art.
 */
private class Emission(
    val origin: Offset,
    /**
     * Moc bat dau, don vi ms, lay tu **dong ho don dieu khong lap vong**
     * (`System.nanoTime`). Truoc day dung dong ho animation chay vong 10s nen
     * cum cham cu **phat lai** moi 10 giay khi man hinh de yen — xem [nowMs].
     */
    val startMs: Long,
)

/** Moc thoi gian don dieu, CUNG goc voi `withFrameMillis` tren Android. */
private fun nowMs(): Long = System.nanoTime() / 1_000_000L

/**
 * Nap spritesheet cua hieu ung (Compose tu cache theo resource id).
 *
 * `null` khi hieu ung khong co animation ([TouchEffectRegistry.None]).
 *
 * ⚠️ `if/else` chu KHONG `?: return` som: day la ham `@Composable`, doi hieu ung
 * tu None sang co sheet lam so lan goi `imageResource` thay doi — phai de trong
 * nhanh `if` de Compose sinh group dung, khong thi call structure bi lech.
 */
@Composable
internal fun rememberEffectSheet(effect: TouchEffect): ImageBitmap? {
    val sheet = effect.sheet
    return if (sheet == null) null else ImageBitmap.imageResource(sheet)
}

/**
 * Lop phu bat cham toan man va chay animation tai diem cham (SKIN_PLAN.md muc 2.5).
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
    val sheet = rememberEffectSheet(effect)
    val active = enabled &&
        !suppressed &&
        effect.id != TouchEffectRegistry.NONE_ID &&
        sheet != null

    val emissions = remember { mutableStateListOf<Emission>() }
    // Tang moi lan cham — dung lam khoa khoi dong lai dong ho. KHONG dung
    // `emissions.size` vi khi da cham tran 8 thi size dung yen, dong ho se khong
    // duoc gia han va animation dang chay bi xoa giua chung.
    var emissionSeq by remember { mutableIntStateOf(0) }
    var frameMs by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current.density

    /*
     * Doc trong `pointerInput` ma KHONG lam key doi -> bo bat su kien KHONG bi
     * huy/tao lai moi lan doi hieu ung. Truoc day key la `effect.id`: bam nhanh
     * qua nhieu o trong tab Effects lam detector bi dung lai lien tuc, cham
     * dang do bi nuot -> cam giac "do, giat".
     */
    val liveActive by rememberUpdatedState(active)

    /*
     * Doi hieu ung (hoac tat hieu ung) -> bo animation dang chay.
     *
     * Khong bo thi animation sinh boi hieu ung cu se duoc ve tiep bang SHEET va
     * tham so cua hieu ung moi — bam nhanh qua vai o la thay hinh doi giua chung.
     */
    LaunchedEffect(active, effect.id) { emissions.clear() }

    /*
     * Dong ho CHI chay khi con animation dang chay (truoc day la `rememberInfiniteTransition`
     * chay suot vong doi app — ve lai moi frame ke ca khi khong co gi de ve).
     * Het animation thi vong lap thoat, khong ton frame nao nua.
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
                    if (emissions.size >= MAX_LIVE_EMISSIONS) {
                        emissions.removeAt(0)
                    }
                    emissions.add(Emission(origin = down.position, startMs = nowMs()))
                    emissionSeq++
                    // TUYET DOI khong consume(): consume la nut/scroll ben duoi chet
                }
            }
        },
    ) {
        content()

        // `sheet` tu duoc smart-cast sang non-null: `active` da chua `sheet != null`
        if (active) {
            /*
             * `graphicsLayer()` = Canvas nay co RenderNode RIENG. Khong co no,
             * moi frame animation se lam ban vung ve cua CA cay UI ben duoi (feed
             * anh, camera preview...) va Compose phai ghi lai toan bo display
             * list -> chinh la doan "lag" khi hieu ung dang chay.
             */
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val sizePx = effect.sizeDp * density
                val durationMs = effect.durationMs.coerceAtLeast(1)

                emissions.forEach { emission ->
                    val progress = (frameMs - emission.startMs).toFloat() / durationMs
                    // progress < 0: frame dau tien sau khi cham, dong ho chua kip
                    // tick lan nao -> bo qua 1 frame thay vi ve o vi tri sai.
                    if (progress < 0f || progress > 1f) return@forEach

                    drawTouchEffectFrame(
                        effect = effect,
                        sheet = sheet,
                        origin = emission.origin,
                        progress = progress,
                        sizePx = sizePx,
                    )
                }
            }
        }
    }
}

/**
 * Ve 1 frame cua spritesheet, canh giua [origin].
 *
 * Tu 2026-08-11 day la TOAN BO phan ve hieu ung: khong con vong lap hat, khong
 * con `sin/cos` tinh quy dao, khong con xoay/scale tung hat. Frame nao duoc ve
 * la do [progress] va [TouchEffect.fps] quyet dinh; chay het frame thi frame
 * cuoi **giu nguyen** cho den het [TouchEffect.durationMs].
 *
 * Dung chung cho lop phu toan man va o demo trong tab Effects — 2 cho chi khac
 * [sizePx], nho vay o demo khong bao gio chay lech voi hieu ung that.
 */
internal fun DrawScope.drawTouchEffectFrame(
    effect: TouchEffect,
    sheet: ImageBitmap,
    origin: Offset,
    progress: Float,
    sizePx: Float,
) {
    val columns = effect.columns
    val rows = effect.rows
    if (columns <= 0 || rows <= 0 || effect.frameCount <= 0) return

    val frameW = sheet.width / columns
    val frameH = sheet.height / rows
    // Anh khong khop luoi khai bao (vd sheet bi resize lech) -> bo ve, khong ve rac
    if (frameW <= 0 || frameH <= 0) return

    /*
     * Frame index suy TRUC TIEP tu thoi gian da troi va fps, roi ep tran ve frame
     * cuoi. Nho cach ep tran nay ma "giu frame cuoi" khong can nhanh xu ly rieng:
     * `durationMs` dai hon thoi gian chay frame bao nhieu thi frame cuoi dung yen
     * bay nhieu, dung luc `fadeStart` lam mo dan.
     */
    val elapsedMs = progress * effect.durationMs
    val frame = (elapsedMs * effect.fps / 1000f).toInt().coerceIn(0, effect.frameCount - 1)

    // coerce: `fadeStart = 1f` (art tu tan) se chia cho 0 -> alpha NaN/-Infinity
    val fadeSpan = (1f - effect.fadeStart).coerceAtLeast(0.001f)
    val alpha = if (progress < effect.fadeStart) {
        1f
    } else {
        (1f - (progress - effect.fadeStart) / fadeSpan).coerceIn(0f, 1f)
    }
    if (alpha <= 0f) return

    // Duoi 1px thi ve cung khong ai thay, bo qua cho re
    val sidePx = sizePx.roundToInt()
    if (sidePx < 1) return

    drawImage(
        image = sheet,
        srcOffset = IntOffset((frame % columns) * frameW, (frame / columns) * frameH),
        srcSize = IntSize(frameW, frameH),
        dstOffset = IntOffset(
            (origin.x - sizePx / 2f).roundToInt(),
            (origin.y - sizePx / 2f).roundToInt(),
        ),
        dstSize = IntSize(sidePx, sidePx),
        alpha = alpha,
    )
}
