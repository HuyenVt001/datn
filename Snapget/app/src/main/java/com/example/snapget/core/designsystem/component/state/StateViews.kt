package com.example.snapget.core.designsystem.component.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.component.button.ChunkyButton
import com.example.snapget.core.designsystem.component.button.ChunkyStyle
import com.example.snapget.core.designsystem.component.lottie.SnapAnim
import com.example.snapget.core.designsystem.component.lottie.SnapLottie
import com.example.snapget.core.designsystem.skin.SkinTheme

/**
 * 3 trang thai tach bach (DESIGN.md muc 0.10): Loading / **Error + Retry** / **Empty + CTA**.
 * Truoc 2026-11-15 nhieu man hien loi mang nhu "trong" (vd feed "No posts to show")
 * va khong co cach thu lai.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Couldn't load",
) {
    StateLayout(
        anim = SnapAnim.NO_INTERNET,
        title = title,
        message = message,
        actionLabel = "Retry",
        onAction = onRetry,
        actionStyle = ChunkyStyle.Secondary,
        modifier = modifier,
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    anim: SnapAnim = SnapAnim.EMPTY,
) {
    StateLayout(
        anim = anim,
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        actionStyle = ChunkyStyle.Primary,
        modifier = modifier,
    )
}

@Composable
private fun StateLayout(
    anim: SnapAnim,
    title: String,
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    actionStyle: ChunkyStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SnapLottie(anim = anim, loop = true, modifier = Modifier.size(140.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = SkinTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = SkinTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            ChunkyButton(
                text = actionLabel,
                onClick = onAction,
                style = actionStyle,
                height = 48.dp,
                modifier = Modifier.widthIn(min = 180.dp),
            )
        }
    }
}
