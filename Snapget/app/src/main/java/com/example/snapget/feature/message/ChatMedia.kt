package com.example.snapget.feature.message

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Tien ich media cho chat: catalog sticker, copy anh tu thu vien,
 * ghi am tin nhan thoai (MediaRecorder) va bubble phat lai (MediaPlayer).
 */

/** Bo sticker co dinh (Twemoji CDN) — content tin STICKER = URL anh. */
val stickerCatalog = listOf(
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f60d.png", // 😍
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f602.png", // 😂
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f62d.png", // 😭
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f525.png", // 🔥
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/2764.png", // ❤
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f44d.png", // 👍
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f389.png", // 🎉
    "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f97a.png", // 🥺
)

/** Trang thai ghi am — [toggle] bat/tat (tu xin quyen RECORD_AUDIO neu chua co). */
class VoiceRecorderState(
    val isRecording: Boolean,
    val toggle: () -> Unit,
)

/**
 * Ghi am tin nhan thoai: cham mic de bat dau, cham lai de dung.
 * Dung xong file .m4a (AAC) duoc tra qua [onRecorded] de upload + gui VOICE.
 */
@Composable
fun rememberVoiceRecorder(onRecorded: (File) -> Unit): VoiceRecorderState {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outFile by remember { mutableStateOf<File?>(null) }

    fun start() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")

            @Suppress("DEPRECATION")
            val newRecorder = MediaRecorder()
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            outFile = file
            isRecording = true
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    fun stop() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Ghi qua ngan -> file hong, bo qua
            outFile = null
        }
        recorder?.release()
        recorder = null
        isRecording = false
        outFile?.takeIf { it.length() > 0 }?.let(onRecorded)
        outFile = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) start()
    }

    DisposableEffect(Unit) {
        onDispose { recorder?.release() }
    }

    return VoiceRecorderState(
        isRecording = isRecording,
        toggle = {
            when {
                isRecording -> stop()
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED -> start()
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )
}

/** Bubble tin VOICE: cham de phat/dung (MediaPlayer stream tu URL Cloudinary). */
@Composable
fun VoiceBubble(
    url: String,
    bubbleColor: Color,
    textColor: Color,
    shape: Shape,
) {
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(bubbleColor)
            .clickable {
                if (playing) {
                    player?.release()
                    player = null
                    playing = false
                } else {
                    try {
                        val newPlayer = MediaPlayer()
                        newPlayer.setDataSource(url)
                        newPlayer.setOnPreparedListener { it.start() }
                        newPlayer.setOnCompletionListener {
                            it.release()
                            player = null
                            playing = false
                        }
                        newPlayer.prepareAsync()
                        player = newPlayer
                        playing = true
                    } catch (_: Exception) {
                        playing = false
                    }
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Stop" else "Play",
            tint = textColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = if (playing) "Playing..." else "Voice message",
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
