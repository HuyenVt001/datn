package com.example.snapget.feature.post

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.sampleItems3
import com.example.snapget.core.designsystem.component.pill.MessageInputPill
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.PostType
import com.example.snapget.core.model.User
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.mapToUser
import com.example.snapget.navigation.Screen
import kotlin.random.Random

/** 1 emoji dang "bay" len sau khi tha reaction (xoa khoi list khi bay xong). */
private data class FlyingEmoji(
    val id: Long,
    val emoji: String,
    val xOffsetDp: Int,
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PostDetailScreen(
    post: Post,
    onBack: () -> Unit,
    navController: NavController,
    friends: List<User> = emptyList(),
    mainViewModel: MainViewModel = hiltViewModel(),
    postViewModel: PostViewModel = hiltViewModel(),
) {
    var showNotifications by remember { mutableStateOf(false) }

    // Reaction: emoji minh vua tha (highlight) + danh sach emoji dang bay
    var selectedEmoji by remember { mutableStateOf<String?>(null) }
    val flyingEmojis = remember { mutableStateListOf<FlyingEmoji>() }
    var selectedUser by remember {
        mutableStateOf<User?>(
            User(
                id = "everyone",
                username = "Everyone",
                avatar = "",
            ),
        )
    }
    // Use the passed onCameraClick instead of navigating to CameraXScreen

    // Get current user from auth state
    val currentUser by mainViewModel.currentUser.collectAsState()
    val data = mapToUser(currentUser)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                MainTopBar(
                    navController = navController,
                    user = data, // Using user 14 as the current user
                    friends = friends,
                    onMessageClick = { navController.navigate(Screen.Message.route) },
                    onProfileClick = {
                        data?.id?.let { userId ->
                            navController.navigate("profile?userId=$userId")
                        } ?: navController.navigate("profile")
                    },
                    onNotificationClick = { showNotifications = true },
                    onUserSelected = { user -> selectedUser = user },
                )
            },
        ) { paddingValues ->
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp)
                    .padding(paddingValues),
            ) {
                // Show camera view or post image based on the localCameraMode state

                // Post image / video (full width)
                post.thumbnailUrl?.let { imageUrl ->
                    val context = LocalContext.current
                    var isPlayingVideo by remember { mutableStateOf(false) }

                    // Khung anh cua moment (neu co) — resolve URL tu catalog frames
                    val frames by postViewModel.frames.collectAsState()
                    LaunchedEffect(post.frameId) {
                        if (post.frameId != null) postViewModel.loadFrames()
                    }
                    val frameImageUrl = frames.find { it.frameId == post.frameId }?.imageUrl

                    Box(
                        modifier = Modifier
                            .height(400.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        if (isPlayingVideo && post.postType == PostType.VIDEO) {
                            // Phat video <=5s bang ExoPlayer, lap lai; cham de dung
                            val exoPlayer = remember {
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(imageUrl))
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
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { isPlayingVideo = false },
                            )
                        } else {
                            AsyncImage(
                                // Video tren Cloudinary: doi duoi sang .jpg de lay poster frame
                                model = if (post.postType == PostType.VIDEO) {
                                    imageUrl.substringBeforeLast('.') + ".jpg"
                                } else {
                                    imageUrl
                                },
                                contentDescription = "Post image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        // Khung overlay phu kin anh/video
                        if (frameImageUrl != null) {
                            AsyncImage(
                                model = frameImageUrl,
                                contentDescription = "Khung anh",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                            )
                        }

                        // Nut play video (an khi dang phat)
                        if (post.postType == PostType.VIDEO && !isPlayingVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(72.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                    )
                                    .clickable { isPlayingVideo = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                        // Caption
                        post.caption?.let { caption ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 24.dp) // Add padding to move it up from bottom
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(24.dp),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    // if the caption length is more than 30 characters, truncate it and add "..."
                                    text = caption.take(30) + if (caption.length > 30) "..." else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // User info and actions
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp), // Consistent spacing between items
                    ) {
                        AsyncImage(
                            model = post.user.avatar,
                            contentDescription = "Profile picture",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )

                        Text(
                            text = post.user.username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = post.createdAt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Message Pill — emoji tha reaction len moment (server cong friend streak)
                MessageInputPill(
                    modifier = Modifier.padding(15.dp),
                    selectedEmoji = selectedEmoji,
                    onEmojiClick = { emoji ->
                        selectedEmoji = emoji
                        postViewModel.react(post.id, emoji)
                        flyingEmojis.add(
                            FlyingEmoji(
                                id = System.nanoTime(),
                                emoji = emoji,
                                xOffsetDp = Random.nextInt(-70, 70),
                            ),
                        )
                    },
                )
            }
        }

        // Emoji bay len tu thanh message roi mo dan (xoa khoi list khi xong)
        flyingEmojis.forEach { flying ->
            key(flying.id) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(flying.id) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    )
                    flyingEmojis.remove(flying)
                }
                Text(
                    text = flying.emoji,
                    fontSize = 34.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(
                            x = flying.xOffsetDp.dp,
                            y = (-160).dp - (280.dp * progress.value),
                        )
                        .alpha(1f - progress.value * progress.value),
                )
            }
        }

        MainBottomBar(
            navController,
            modifier = Modifier.align(Alignment.BottomCenter),
            items = sampleItems3,
        )
    }
}
