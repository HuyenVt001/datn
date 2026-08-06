package com.example.snapget.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.pill.extraReactionEmojis
import com.example.snapget.core.designsystem.component.pill.quickReactionEmojis
import com.example.snapget.core.designsystem.skin.SkinTheme

// ⚠️ `Color.White` trong file nay la CO Y, KHONG doi sang token skin:
// chu/icon o day nam de len ANH hoac CAMERA cua nguoi dung nen phai trang
// that o MOI skin. Doi theo `SkinTheme.colors.textPrimary` thi skin nen sang
// se lam chung chim vao anh. Mau cua NEN app trong file nay van dung token.

/**
 * Xem media cua tin nhan FULL-SCREEN (bam anh trong chat):
 * - Anh: hien nguyen ty le (khong crop vuong nhu bubble), pinch-zoom + keo pan.
 * - Video (attachment tin reply bai dang): phat ExoPlayer lap lai.
 * Dong bang nut X hoac back.
 */
// RESIZE_MODE_FIT cua media3 con gan nhan UnstableApi — opt-in co chu dich
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MediaViewerDialog(
    url: String,
    isVideo: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (isVideo) {
                val context = LocalContext.current
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(url))
                        repeatMode = Player.REPEAT_MODE_ONE
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Pinch-zoom (1x..5x) + pan; zoom ve 1x thi reset pan
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = url,
                    contentDescription = "Full photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * Menu khi LONG-PRESS 1 tin nhan (kieu Messenger): hang emoji tha reaction
 * (tha lai cung emoji dang co = go, server toggle) + hanh dong "Reply" (neu
 * truyen [onReply]) de trich dan tin nay khi gui tin moi.
 */
@Composable
fun ReactionPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onReply: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                // Gop 3 emoji nhanh + bo mo rong, chia hang 6 emoji cho vua dialog
                (quickReactionEmojis + extraReactionEmojis).chunked(6).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onPick(emoji) }
                                    .padding(8.dp),
                            )
                        }
                    }
                }

                if (onReply != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SkinTheme.shapes.input)
                            .clickable { onReply() }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Reply",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hang reaction da tha tren 1 tin nhan: gom theo emoji + so luong (vd "💛2 😂1").
 * Bam vao = mo picker (doi/go reaction cua minh). Null/rong -> khong ve gi.
 */
@Composable
fun MessageReactionsRow(
    reactions: Map<String, String>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isNullOrEmpty()) return
    val grouped = reactions.values.groupingBy { it }.eachCount()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() },
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            grouped.forEach { (emoji, count) ->
                Text(
                    text = if (count > 1) "$emoji$count" else emoji,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}
