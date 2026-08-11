package com.example.snapget.feature.gacha

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.snapget.R
import com.example.snapget.core.designsystem.effect.TouchEffect
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.effect.rememberEffectSheet
import com.example.snapget.core.designsystem.skin.SkinRegistry
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.RollOutcomeDto
import com.example.snapget.core.network.dto.RollResultDto
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Khoang cach giua 2 la lat lien tiep. */
private const val FLIP_INTERVAL_MS = 250L

/** Thoi gian 1 la xoay tu mat sau sang mat truoc. */
private const val FLIP_DURATION_MS = 380

/**
 * Chieu cao dong chu duoi la bai, tinh theo BE NGANG la bai.
 *
 * Co dinh chu khong bo theo noi dung: neu de tu gian thi luc la bai lat xong,
 * ten hien ra se day ca hang tut xuong — nhin nhu luoi bi giat.
 */
private const val CAPTION_RATIO = 0.44f

/** Anh mat truoc (co khung + chu bac ve san, giua de trong cho anh vat pham). */
@DrawableRes
private fun cardFrontAsset(tier: String): Int = when (tier) {
    "SSR" -> R.drawable.gacha_frontssrcard
    "SR" -> R.drawable.gacha_frontsrcard
    "R" -> R.drawable.gacha_frontrcard
    else -> R.drawable.gacha_frontncard
}

/**
 * Anh mat sau. **Mau mat sau da bao bac** — nguoi choi thay vien vang la biet
 * sap ra SSR truoc khi la bai lat. Day la y do cua bo asset (co du 4 mat sau
 * cho 4 bac, khong co mat sau trung tinh nao).
 */
@DrawableRes
private fun cardBackAsset(tier: String): Int = when (tier) {
    "SSR" -> R.drawable.gacha_backsidessrcard
    "SR" -> R.drawable.gacha_backsidesrcard
    "R" -> R.drawable.gacha_backsidercard
    else -> R.drawable.gacha_backsidencard
}

/**
 * Man ket qua quay (GACHA_PLAN.md muc 6.5).
 *
 * - x1: 1 la bai to o giua
 * - x10: bo cuc **2–3–3–2** theo ban thiet ke `PreviewGachaScreen_Gachax10.png`,
 *   **lat lan luot** ~250ms/la, co nut **Skip** lat thang het
 *
 * Lop phu de mo chu khong dac: art cua man gacha ben duoi van thay duoc, dung
 * nhu ban thiet ke.
 *
 * Animation bang Compose thuan — khong them dependency (thong nhat SKIN_PLAN).
 */
@Composable
fun GachaResultOverlay(
    outcome: RollOutcomeDto,
    onDismiss: () -> Unit,
) {
    val results = outcome.results
    var revealed by remember(outcome.rollId) { mutableIntStateOf(0) }
    val allRevealed = revealed >= results.size

    // Lat lan luot; bam Skip thi nhay thang toi het
    LaunchedEffect(outcome.rollId, revealed) {
        if (revealed < results.size) {
            delay(FLIP_INTERVAL_MS)
            revealed++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            // Chan cham xuyen xuong man ben duoi
            .clickable(enabled = false) {},
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ResultCardGrid(
                results = results,
                revealed = revealed,
                // weight: luoi nhan DUNG phan chieu cao con lai sau khi tru phan
                // chan man, roi tu thu nho la bai cho vua — 10 la + ten cao ~730dp
                // o co goc, may man nho se bi cat mat hang duoi neu khong co buoc nay.
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            )

            Spacer(Modifier.height(20.dp))

            // Tong hoan + so du chi hien khi DA lat het: hien tu dau la nhin
            // tong hoan tien doan duoc so la trung truoc ca khi lat.
            if (allRevealed && outcome.refundTotal > 0) {
                Text(
                    text = "+%,d Astrite returned".format(outcome.refundTotal),
                    color = SkinTheme.colors.accentGold,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (allRevealed) {
                Text(
                    text = "Balance: %,d Astrite".format(outcome.astriteAfter),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (allRevealed) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkinTheme.colors.accent,
                        contentColor = SkinTheme.colors.onAccent,
                    ),
                    shape = SkinTheme.shapes.pill,
                ) {
                    Text(text = "OK", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = { revealed = results.size }) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Luoi la bai.
 *
 * Ban thiet ke ve cum 10 la **lech trai ~15px**; o day dung `Row` bo goi noi
 * dung + `horizontalAlignment = CenterHorizontally` nen moi hang tu can giua
 * tuyet doi theo be ngang man hinh (user chot).
 */
@Composable
private fun ResultCardGrid(
    results: List<RollResultDto>,
    revealed: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val single = results.size <= 1

        var from = 0
        val rows = cardRows(results.size).mapNotNull { count ->
            if (from >= results.size) {
                null
            } else {
                val to = minOf(from + count, results.size)
                val slice = (from until to).toList()
                from = to
                slice
            }
        }

        // x1 dung la bai to hon han cho ra chat "mo qua"; x10 giu dung ti le
        // 217/1080 cua ban thiet ke.
        val wantWidth = if (single) maxWidth * 0.46f else maxWidth * (CARD_W / BG_W)
        val wantColGap = maxWidth * ((CARD_COL_PITCH - CARD_W) / BG_W)
        val wantRowGap = maxWidth * ((CARD_ROW_PITCH - CARD_H) / BG_W)

        // Neu chieu cao khong du thi thu nho DEU ca cum (la bai + khe + dong ten)
        // thay vi de hang cuoi bi cat.
        val wantRowHeight = wantWidth * (CARD_H / CARD_W) + wantWidth * CAPTION_RATIO
        val wantHeight = wantRowHeight * rows.size + wantRowGap * (rows.size - 1)
        val shrink = if (wantHeight > maxHeight) maxHeight / wantHeight else 1f

        val cardWidth = wantWidth * shrink
        val colGap = wantColGap * shrink
        val rowGap = wantRowGap * shrink

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            rows.forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(colGap)) {
                    indices.forEach { i ->
                        ResultCard(
                            entry = results[i],
                            revealed = i < revealed,
                            width = cardWidth,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1 la bai: lat 3D tu mat sau sang mat truoc.
 *
 * Ten vat pham nam **duoi la bai, ngoai khung** (user chot) — mat truoc da co
 * khung + chu bac ve san, khong con cho de chen ten ma khong de len art.
 */
@Composable
private fun ResultCard(
    entry: RollResultDto,
    revealed: Boolean,
    width: Dp,
) {
    val density = LocalDensity.current
    val rotation by animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(FLIP_DURATION_MS),
        label = "card-flip",
    )

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_W / CARD_H)
                .graphicsLayer {
                    rotationY = rotation
                    // Khong dat cameraDistance thi goc nhin qua gan, la bai
                    // phinh to meo mo o giua chung khi xoay.
                    cameraDistance = 14f * density.density
                },
        ) {
            if (rotation <= 90f) {
                Image(
                    painter = painterResource(cardBackAsset(entry.tier)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Nua sau cua cu lat: lat nguoc lai 180° cho mat truoc khong bi soi guong
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                    CardFront(entry = entry)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(width * CAPTION_RATIO),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Ten hien khi MAT TRUOC da lo ra (qua nua cu lat), khong phai khi
            // bat dau lat — hien som la lo ket qua truoc ca khi thay la bai.
            CardCaption(entry = entry, revealed = rotation > 90f, width = width)
        }
    }
}

/** Mat truoc: khung asset + anh vat pham dat vao o trong o giua khung. */
@Composable
private fun CardFront(entry: RollResultDto) {
    val skinThumb = remember(entry.itemType, entry.refId) {
        localSkinThumbnail(entry.itemType, entry.refId)
    }
    val effect = remember(entry.itemType, entry.refId) {
        localEffect(entry.itemType, entry.refId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(cardFrontAsset(entry.tier)),
            contentDescription = entry.itemName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Bac N: vien pha le Astrite DA duoc ve san trong asset, so luong hien
        // o dong chu duoi la bai -> khong ve gi them vao o.
        if (entry.tier == "N") return@Box

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val slotW = maxWidth * CARD_SLOT_W_RATIO
            val slotH = maxHeight * CARD_SLOT_H_RATIO
            val slotTop = maxHeight * CARD_SLOT_CENTER_Y_RATIO - slotH / 2f

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = slotTop)
                    .width(slotW)
                    .height(slotH),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    skinThumb != null -> Image(
                        painter = painterResource(skinThumb),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    effect != null -> EffectThumbnail(
                        effect = effect,
                        modifier = Modifier.fillMaxSize(),
                    )

                    entry.imageUrl != null -> AsyncImage(
                        model = entry.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Dong chu duoi la bai: ten vat pham (hoac so Astrite voi bac N) + nhan trung.
 *
 * Co chu tinh theo BE NGANG la bai chu khong co dinh — luoi 10 la co the bi thu
 * nho tren may man be, chu co dinh se tran ra ngoai o da danh san.
 */
@Composable
private fun CardCaption(entry: RollResultDto, revealed: Boolean, width: Dp) {
    if (!revealed) return

    val nameSp = (width.value * 0.115f).coerceIn(8f, 18f).sp
    val badgeSp = (width.value * 0.10f).coerceIn(7f, 14f).sp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when (entry.tier) {
                "N" -> "+%,d Astrite".format(entry.astriteAmount ?: 0)
                else -> entry.itemName.orEmpty()
            },
            color = Color.White,
            fontSize = nameSp,
            lineHeight = nameSp * 1.2f,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            entry.isDuplicate && entry.refundAstrite > 0 -> Text(
                text = "Duplicate +%,d".format(entry.refundAstrite),
                color = SkinTheme.colors.accentGold,
                fontSize = badgeSp,
                lineHeight = badgeSp * 1.2f,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            entry.tier != "N" -> Text(
                text = "NEW",
                color = GachaRarity.color(entry.tier),
                fontSize = badgeSp,
                lineHeight = badgeSp * 1.2f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/*
 * Anh dai dien nam TRONG APK cua skin / hieu ung, tra theo `(itemType, refId)`.
 *
 * Khung anh co `imageUrl` tren Cloudinary, con **skin va hieu ung thi khong** —
 * asset cua chung dong goi trong APK (dung tinh than "admin khong sua duoc vat
 * pham nay"). Khong co 2 ham duoi thi quay trung skin/hieu ung chi hien o trong.
 *
 * `firstOrNull` chiu duoc refId la (vat pham cua ban server moi hon ban app dang
 * cai) bang cach tra `null` -> o trong, la bai van hien dung bac.
 *
 * Vi sao TACH lam 2 ham thay vi 1 ham tra `@DrawableRes Int?` nhu truoc: tu
 * 2026-08-11 hieu ung la **spritesheet** chu khong con 1 anh hat don le, ve ca
 * file bang `painterResource` se ra mot cai luoi 8 o. Phai cat 1 frame -> can ca
 * doi tuong `TouchEffect` (biet `columns`/`thumbFrame`), khong chi resource id.
 */

/** Anh dai dien cua skin (van la 1 drawable don). */
@DrawableRes
private fun localSkinThumbnail(itemType: String?, refId: String?): Int? {
    if (itemType != "SKIN") return null
    val id = refId?.toIntOrNull() ?: return null
    return SkinRegistry.all.firstOrNull { it.id == id }?.thumbnail
}

/** Hieu ung touch tuong ung, de cat frame lam anh dai dien. */
private fun localEffect(itemType: String?, refId: String?): TouchEffect? {
    if (itemType != "EFFECT") return null
    val id = refId?.toIntOrNull() ?: return null
    return TouchEffectRegistry.all.firstOrNull { it.id == id && it.sheet != null }
}

/**
 * Anh dai dien cua hieu ung: cat dung frame [TouchEffect.thumbFrame] tu
 * spritesheet — frame animation no to nhat, chu khong phai frame 0 (frame 0 gan
 * nhu trong tron, nhin ra o rong).
 *
 * KHONG tint theo mau bac nua: tu 2026-08-11 sheet da co mau san, tint la bet
 * ca bo hoa nhieu mau thanh mot khoi mot mau.
 */
@Composable
private fun EffectThumbnail(effect: TouchEffect, modifier: Modifier = Modifier) {
    val sheet = rememberEffectSheet(effect)
    val columns = effect.columns
    val rows = effect.rows
    if (sheet == null || columns <= 0 || rows <= 0) return

    val frameW = sheet.width / columns
    val frameH = sheet.height / rows
    val frame = effect.thumbFrame.coerceIn(0, (effect.frameCount - 1).coerceAtLeast(0))

    Canvas(modifier = modifier) {
        // Vua khung o ma khong meo: lay canh ngan hon cua o lam co
        val side = minOf(size.width, size.height)
        drawImage(
            image = sheet,
            srcOffset = IntOffset((frame % columns) * frameW, (frame / columns) * frameH),
            srcSize = IntSize(frameW, frameH),
            dstOffset = IntOffset(
                ((size.width - side) / 2f).roundToInt(),
                ((size.height - side) / 2f).roundToInt(),
            ),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
        )
    }
}
