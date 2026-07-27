package com.example.snapget.feature.post

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.mapToUser
import com.example.snapget.core.util.relativeTimeShort
import com.example.snapget.navigation.Screen
import kotlin.random.Random

/** 1 emoji dang "bay" len sau khi tha reaction (xoa khoi list khi bay xong). */
data class FlyingEmoji(
    val id: Long,
    val emoji: String,
    val xOffsetDp: Int,
)

/** Tao 1 emoji bay voi vi tri ngang ngau nhien (helper dung o ca pager va detail). */
fun newFlyingEmoji(emoji: String): FlyingEmoji = FlyingEmoji(
    id = System.nanoTime(),
    emoji = emoji,
    xOffsetDp = Random.nextInt(-70, 70),
)

/**
 * Overlay emoji bay len tu day man hinh roi mo dan (1.4s) — dat trong Box goc man.
 * Dung chung cho PostScreen (pager) va PostDetailScreen (profile).
 */
@Composable
fun BoxScope.FlyingEmojiOverlay(flyingEmojis: SnapshotStateList<FlyingEmoji>) {
    flyingEmojis.toList().forEach { flying ->
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
}

/**
 * Noi dung 1 post (khop anh mau 2026-07-26): anh/video vuong bo 20dp + khung +
 * caption de day anh, duoi la hang tac gia (avatar 40 + ten Bold + "1d" xam).
 * KHONG chua top bar / message pill / bottom bar — man chua tu bo tri
 * (PostScreen dat trong VerticalPager, PostDetailScreen dat trong Scaffold).
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PostDetailContent(
    post: Post,
    frameImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        // Post image / video (full width)
        post.thumbnailUrl.takeIf { it.isNotBlank() }?.let { imageUrl ->
            val context = LocalContext.current
            var isPlayingVideo by remember(post.id) { mutableStateOf(false) }

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
                        contentDescription = "Photo frame",
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

        // Hang tac gia: avatar + ten Bold + thoi gian tuong doi kieu "1d" (khop mau)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = avatarOrDefault(post.user.avatar, post.user.username),
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
                    text = relativeTimeShort(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Man chi tiet 1 post DOC LAP — con dung tu UserProfileScreen (bam o calendar).
 * Feed chinh KHONG dung man nay nua (2026-07-26) — PostScreen hien pager
 * full-screen voi PostDetailContent ben trong.
 */
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
    val context = LocalContext.current

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

    // Get current user from auth state
    val currentUser by mainViewModel.currentUser.collectAsState()
    val data = mapToUser(currentUser)

    // Dong bo avatar/ten cua MINH cho top bar (fix 2026-07-27): man nay mo tu
    // calendar profile co MainViewModel scope RIENG — khong goi fetchCurrentUser
    // thi currentUser rong -> avatar top bar sai/khong dong bo voi cac man khac
    LaunchedEffect(Unit) {
        mainViewModel.fetchCurrentUser()
    }

    // Khung anh cua moment (neu co) — resolve URL tu catalog frames
    val frames by postViewModel.frames.collectAsState()
    LaunchedEffect(post.frameId) {
        if (post.frameId != null) postViewModel.loadFrames()
    }
    val frameImageUrl = frames.find { it.frameId == post.frameId }?.imageUrl

    // Toast ket qua gui tin nhan/xoa (one-shot)
    val actionMessage by postViewModel.actionMessage.collectAsState()
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            postViewModel.clearActionMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                MainTopBar(
                    navController = navController,
                    user = data,
                    friends = friends,
                    onMessageClick = { navController.navigate(Screen.Message.route) },
                    onProfileClick = {
                        data?.id?.let { userId ->
                            navController.navigate("profile?userId=$userId")
                        } ?: navController.navigate("profile")
                    },
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
                PostDetailContent(
                    post = post,
                    frameImageUrl = frameImageUrl,
                )

                // Message Pill — emoji tha reaction + go text nhan tin toi tac gia.
                // AN voi bai cua CHINH MINH (fix 2026-07-27); luc currentUser CHUA tai
                // xong (id "unknown") cung an de khong loe pill tren bai cua minh
                if (data.id != "unknown" && post.user.id != data.id) {
                    MessageInputPill(
                        modifier = Modifier.padding(top = 24.dp),
                        selectedEmoji = selectedEmoji,
                        onEmojiClick = { emoji ->
                            selectedEmoji = emoji
                            postViewModel.react(post.id, emoji)
                            flyingEmojis.add(newFlyingEmoji(emoji))
                        },
                        onSendMessage = { text ->
                            // Reply gui KEM anh/video cua bai (attachment)
                            postViewModel.sendMessageToAuthor(post, text)
                        },
                    )
                }
            }
        }

        // Emoji bay len tu thanh message roi mo dan
        FlyingEmojiOverlay(flyingEmojis)

        MainBottomBar(
            navController,
            modifier = Modifier.align(Alignment.BottomCenter),
            items = sampleItems3,
        )
    }
}
