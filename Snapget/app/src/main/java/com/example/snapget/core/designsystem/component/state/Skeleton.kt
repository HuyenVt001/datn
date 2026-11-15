package com.example.snapget.core.designsystem.component.state

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.motion.rememberReducedMotion
import com.example.snapget.core.designsystem.skin.SkinTheme

/**
 * Skeleton loading (P5 Playful v2 — DESIGN.md 0.10): khoi xam `surfaceVariant`
 * "tho" (alpha 0.45 <-> 1) giu dung bo cuc noi dung sap hien -> khong nhay layout
 * nhu spinner giua man. Reduced motion: dung yen.
 */
fun Modifier.skeleton(shape: Shape): Modifier = composed {
    val reduced = rememberReducedMotion()
    val alpha = if (reduced) {
        0.7f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val a by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        a
    }
    this
        .graphicsLayer { this.alpha = alpha }
        .clip(shape)
        .background(SkinTheme.colors.surfaceVariant)
}

/** 1 khoi skeleton chu nhat bo goc. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp,
    shape: Shape = SkinTheme.shapes.pill,
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .skeleton(shape),
    )
}

/** Skeleton luoi anh vuong (Feed grid 3 cot / Shop 2 cot). */
@Composable
fun SkeletonGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    count: Int = columns * 4,
    aspectRatio: Float = 1f,
    spacing: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = contentPadding,
        userScrollEnabled = false,
        modifier = modifier.semantics { contentDescription = "Loading" },
    ) {
        items(count) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .skeleton(SkinTheme.shapes.image),
            )
        }
    }
}

/** Skeleton danh sach hang avatar + 2 dong chu (Messages, danh sach ban). */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    avatarSize: Dp = 50.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(rows) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(avatarSize).skeleton(CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // Do dai xen ke cho tu nhien
                    SkeletonBlock(width = if (i % 2 == 0) 140.dp else 110.dp, height = 16.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(height = 12.dp, modifier = Modifier.padding(end = if (i % 3 == 0) 40.dp else 80.dp))
                }
            }
        }
    }
}

/** Skeleton cho man the doc (Quest): 1 khoi hero + N the. */
@Composable
fun SkeletonCards(
    modifier: Modifier = Modifier,
    heroHeight: Dp = 220.dp,
    cards: Int = 3,
    cardHeight: Dp = 92.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(heroHeight).skeleton(SkinTheme.shapes.card))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(width = 140.dp, height = 18.dp)
        repeat(cards) {
            Box(Modifier.fillMaxWidth().height(cardHeight).skeleton(SkinTheme.shapes.card))
        }
    }
}
