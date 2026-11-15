package com.example.snapget.core.designsystem.skin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// ============================ Hoa van nen chat ============================

/**
 * Hoa van nen chat (Skin v2 — 2026-11-15): rai CHINH particle cua skin thanh luoi
 * lech (moi o 76dp 1 hat, lech/xoay/co ngau nhien co dinh seed) o do mo
 * [SkinDecor.patternAlpha] — khong can them asset nao.
 *
 * Kem 1 lop toi nhe (`background` 35%) khi skin co anh nen: anh nen + hat bay o
 * backdrop kha "dong", bong bong chat can nen diu hon de doc.
 *
 * Ve 1 lan roi cache (drawWithCache) — chi tinh lai khi doi kich thuoc/skin.
 * Gan vao modifier GOC cua man chat (truoc `imePadding`) de phu ca top bar + o nhap.
 * Skin khong co particle/anh nen (Default) -> khong ve gi.
 */
@Composable
fun Modifier.skinChatWallpaper(): Modifier {
    val skin = SkinTheme.skin
    val dim = if (skin.images.screenBackground != null) {
        skin.colors.background.copy(alpha = CHAT_DIM_ALPHA)
    } else {
        Color.Transparent
    }
    val res = skin.decor.particle
    if (res == null) {
        return if (dim.alpha > 0f) this.background(dim) else this
    }
    val image: ImageBitmap = ImageBitmap.imageResource(res)
    val decor = skin.decor
    val flipAlternate = decor.particleMotion == ParticleMotion.DRIFT
    val srcSize = IntSize(image.width, image.height)

    return this.drawWithCache {
        val cell = PATTERN_CELL.toPx()
        val jitter = cell * 0.28f
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        // Seed theo vi tri o -> hoa van dung yen khi ve lai / doi man qua lai
        val items = buildList {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val rnd = Random(r * 7919 + c * 104729 + decor.hashCode())
                    // Hang le lui nua o -> luoi so le, nhin tu nhien hon luoi vuong
                    val baseX = c * cell + if (r % 2 == 1) cell / 2f else 0f
                    add(
                        PatternItem(
                            x = baseX + (rnd.nextFloat() * 2f - 1f) * jitter,
                            y = r * cell + (rnd.nextFloat() * 2f - 1f) * jitter,
                            sizePx = (14f + rnd.nextFloat() * 10f).dp.toPx(),
                            rotation = rnd.nextFloat() * 360f,
                            flip = if (flipAlternate && (r + c) % 2 == 1) -1f else 1f,
                        ),
                    )
                }
            }
        }
        onDrawBehind {
            if (dim.alpha > 0f) drawRect(dim)
            items.forEach { p ->
                val half = p.sizePx / 2f
                translate(p.x, p.y) {
                    rotate(p.rotation, pivot = Offset.Zero) {
                        scale(p.flip, 1f, pivot = Offset.Zero) {
                            drawImage(
                                image = image,
                                srcSize = srcSize,
                                dstOffset = IntOffset(-half.toInt(), -half.toInt()),
                                dstSize = IntSize(p.sizePx.toInt(), p.sizePx.toInt()),
                                alpha = decor.patternAlpha,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class PatternItem(
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val rotation: Float,
    val flip: Float,
)

private val PATTERN_CELL = 76.dp
private const val CHAT_DIM_ALPHA = 0.35f

// ============================ Ornament mep sheet ============================

/**
 * Drag handle cua bottom sheet co dai ornament cua skin o mep tren (Skin v2).
 *
 * Cat phan TREN cua ornament (noi art day nhat: tuyet dong / tan la) thanh dai
 * [SHEET_ORNAMENT_HEIGHT], mo dan xuong [sheetColor] o nua duoi de noi muot vao
 * than sheet. Shape cua sheet tu clip 2 goc bo tron. Skin khong co ornament
 * (Default) -> chi ve [handle] nhu cu, kich thuoc khong doi.
 *
 * Dung: `ModalBottomSheet(dragHandle = { SkinSheetDragHandle(sheetColor = …) })`.
 *
 * @param sheetColor PHAI trung `containerColor` cua sheet (de dai mo dan khong lo vet).
 * @param handle tay keo goc ben tren dai ornament (mac dinh cua M3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheetDragHandle(
    sheetColor: Color,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit = { BottomSheetDefaults.DragHandle() },
) {
    val decor = SkinTheme.skin.decor
    val ornament = decor.ornament
    if (ornament == null) {
        handle()
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SHEET_ORNAMENT_HEIGHT),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painter = painterResource(ornament),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = decor.ornamentAlpha,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to sheetColor,
                    ),
                ),
        )
        handle()
    }
}

private val SHEET_ORNAMENT_HEIGHT = 56.dp
