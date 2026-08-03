package com.example.snapget.core.designsystem.component.video

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * "Anh GIF" (chot 2026-08-03): clip <=3s phat TU DONG, LAP VO HAN, KHONG TIENG,
 * khong nut dieu khien — coi nhu 1 tam anh biet chuyen dong. Dung o MOI cho hien
 * moment VIDEO: feed pager, man xem post cu, preview truoc khi dang.
 *
 * [source] = URL http(s) (moment tren Cloudinary) HOAC duong dan file local
 * (clip vua quay, chua upload).
 */
@Composable
fun GifVideoPlayer(
    source: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val exoPlayer = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            val uri = if (source.startsWith("http")) {
                Uri.parse(source)
            } else {
                Uri.fromFile(File(source))
            }
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE // lap vo han nhu GIF
            volume = 0f // GIF khong co tieng
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(source) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                // Phu kin khung vuong (crop) giong anh thuong ContentScale.Crop
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier,
    )
}
