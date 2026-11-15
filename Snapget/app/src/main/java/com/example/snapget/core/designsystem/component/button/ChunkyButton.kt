package com.example.snapget.core.designsystem.component.button

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.feedback.rememberFeedback
import com.example.snapget.core.designsystem.motion.rememberReducedMotion
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.skin.darken

/** Bien the mau cua [ChunkyButton] — DESIGN.md 0.3. */
enum class ChunkyStyle { Primary, Secondary, Danger }

/**
 * Nut "chunky" 3D (DESIGN.md muc 0.3): than nut + DAY day [depth] mau toi hon;
 * nhan -> than dich xuong [depth], day bien mat = cam giac "lun" nhu phim bam.
 * Kem haptic tick. Disabled = phang, khong day, chu `textSecondary`.
 *
 * KHONG dung cho nut chup 80dp (giu vong tron vien vang — ban sac camera).
 */
@Composable
fun ChunkyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChunkyStyle = ChunkyStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
    depth: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = SkinTheme.colors
    val (face, bottom, onFace) = when (style) {
        ChunkyStyle.Primary -> Triple(colors.accent, colors.accentShadow, colors.onAccent)
        ChunkyStyle.Secondary -> Triple(colors.pill, colors.surfaceVariant.darken(0.6f), colors.textPrimary)
        ChunkyStyle.Danger -> Triple(colors.error, colors.error.darken(0.7f), colors.onAccent)
    }
    val active = enabled && !loading
    val faceColor = if (enabled) face else colors.surfaceVariant
    val contentColor = if (enabled) onFace else colors.textSecondary

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val effectiveDepth = if (enabled) depth else 0.dp
    val offsetY by animateDpAsState(
        targetValue = if (pressed && active) effectiveDepth else 0.dp,
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(70),
        label = "chunkyPress",
    )
    val feedback = rememberFeedback()
    val shape = SkinTheme.shapes.input

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .height(height + effectiveDepth)
            .clickable(
                interactionSource = interaction,
                indication = null, // phan hoi = "lun" + haptic (thay ripple)
                enabled = active,
                role = Role.Button,
                onClick = {
                    feedback.tap()
                    onClick()
                },
            ),
    ) {
        // Day (bong) — co dinh o day duoi
        if (effectiveDepth > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .offset(y = effectiveDepth)
                    .clip(shape)
                    .background(bottom),
            )
        }
        // Than nut — dich xuong khi nhan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = offsetY)
                .clip(shape)
                .background(faceColor)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = contentColor,
                    )
                } else {
                    content()
                }
            }
        }
    }
}

/** Ban tien loi: nut chunky chi co chu (Sign In, Retry, Next...). */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChunkyStyle = ChunkyStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
    depth: Dp = 4.dp,
) {
    ChunkyButton(
        onClick = onClick,
        modifier = modifier,
        style = style,
        enabled = enabled,
        loading = loading,
        height = height,
        depth = depth,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = Color.Unspecified, // lay LocalContentColor
            maxLines = 1,
        )
    }
}
