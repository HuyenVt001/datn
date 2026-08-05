package com.example.snapget.feature.gacha

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.RollOutcomeDto
import com.example.snapget.core.network.dto.RollResultDto
import kotlinx.coroutines.delay

/** Khoang cach giua 2 the lat lien tiep (GACHA_PLAN.md muc 6.5). */
private const val FLIP_INTERVAL_MS = 250L

/**
 * Man ket qua quay (GACHA_PLAN.md muc 6.5).
 *
 * - x1: 1 the o giua
 * - x10: luoi 5×2, **lat lan luot** ~250ms/o, co nut **Skip** hien thang ca luoi
 *
 * Animation bang Compose thuan — khong them dependency (thong nhat SKIN_PLAN).
 */
@Composable
fun GachaResultOverlay(
    outcome: RollOutcomeDto,
    onDismiss: () -> Unit,
) {
    val results = outcome.results
    var revealed by remember(outcome.rollId) { mutableIntStateOf(if (results.size == 1) 1 else 0) }
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
            .background(SkinTheme.colors.overlay.copy(alpha = 0.92f))
            // Chan cham xuyen xuong man ben duoi
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (results.size == 1) "Result" else "Results",
                color = SkinTheme.colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            if (results.size == 1) {
                Box(modifier = Modifier.fillMaxWidth(0.55f)) {
                    ResultCard(entry = results[0], revealed = true)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(results) { index, entry ->
                        ResultCard(entry = entry, revealed = index < revealed)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (outcome.refundTotal > 0) {
                Text(
                    text = "+${outcome.refundTotal} Astrite returned",
                    color = SkinTheme.colors.accentGold,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = "Balance: ${outcome.astriteAfter} Astrite",
                color = SkinTheme.colors.textSecondary,
            )

            Spacer(Modifier.height(20.dp))

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
                        color = SkinTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 1 the ket qua — to theo mau pham chat, bac N hien thang so Astrite. */
@Composable
private fun ResultCard(entry: RollResultDto, revealed: Boolean) {
    val tierColor = GachaRarity.color(entry.tier)
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.8f,
        animationSpec = tween(200),
        label = "card-reveal",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .scale(scale)
                .alpha(if (revealed) 1f else 0.25f)
                .clip(SkinTheme.shapes.input)
                .background(SkinTheme.colors.surfaceVariant)
                .border(
                    // SSR day vien nhat — nhin phat biet ngay trung to
                    width = if (entry.tier == "SSR") 3.dp else 2.dp,
                    color = tierColor,
                    shape = SkinTheme.shapes.input,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!revealed) return@Box

            when {
                entry.tier == "N" -> {
                    Text(
                        text = "+${entry.astriteAmount ?: 0}",
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }

                entry.imageUrl != null -> {
                    AsyncImage(
                        model = entry.imageUrl,
                        contentDescription = entry.itemName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }

                else -> {
                    // Vat pham chua co anh dai dien (admin chua upload) -> hien bac
                    Text(
                        text = entry.tier,
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
            }

            Text(
                text = GachaRarity.label(entry.tier),
                color = SkinTheme.colors.onAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(SkinTheme.shapes.pill)
                    .background(tierColor)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }

        if (revealed) {
            Text(
                text = when {
                    entry.tier == "N" -> "Astrite"
                    entry.isDuplicate -> "Dup +${entry.refundAstrite}"
                    else -> entry.itemName ?: "?"
                },
                color = if (entry.isDuplicate) {
                    SkinTheme.colors.textSecondary
                } else {
                    SkinTheme.colors.textPrimary
                },
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Hang thong tin trong popup Rule. */
@Composable
internal fun RuleRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(text = value, color = SkinTheme.colors.textPrimary)
    }
}
