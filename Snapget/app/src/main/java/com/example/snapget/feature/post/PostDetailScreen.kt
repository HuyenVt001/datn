package com.example.snapget.feature.post

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.component.pill.MessageInputPill
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.designsystem.component.video.GifVideoPlayer
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.PostType
import com.example.snapget.core.model.User
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.MediaActions
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.mapToUser
import com.example.snapget.core.util.relativeTimeShort
import com.example.snapget.navigation.Screen
import kotlin.random.Random
import kotlinx.coroutines.launch

// ⚠️ `Color.White` trong file nay la CO Y, KHONG doi sang token skin:
// chu/icon o day nam de len ANH hoac CAMERA cua nguoi dung nen phai trang
// that o MOI skin. Doi theo `SkinTheme.colors.textPrimary` thi skin nen sang
// se lam chung chim vao anh. Mau cua NEN app trong file nay van dung token.

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
                    // Ban lambda: emoji bay chay animation MOI FRAME — ban
                    // `offset(x, y)` bat recompose lai Text moi frame, ban nay
                    // chi chay lai khau dat vi tri.
                    .offset {
                        IntOffset(
                            flying.xOffsetDp.dp.roundToPx(),
                            ((-160).dp - (280.dp * progress.value)).roundToPx(),
                        )
                    }
                    .alpha(1f - progress.value * progress.value),
            )
        }
    }
}

/**
 * Noi dung 1 post (khop anh mau 2026-07-26): anh/GIF vuong bo 20dp + khung +
 * caption de day anh, duoi la hang tac gia (avatar 40 + ten Bold + "1d" xam).
 * Moment VIDEO = "anh GIF": tu phat + lap vo han + khong tieng (2026-08-03).
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
        // Post image / GIF (full width)
        post.thumbnailUrl.takeIf { it.isNotBlank() }?.let { imageUrl ->
            Box(
                modifier = Modifier
                    .height(400.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                if (post.postType == PostType.VIDEO) {
                    // "Anh GIF": phat TU DONG + lap vo han + khong tieng (2026-08-03 —
                    // truoc day phai cham nut play moi xem duoc)
                    GifVideoPlayer(
                        source = imageUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(SkinTheme.shapes.image),
                    )
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Post image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(SkinTheme.shapes.image),
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
                            .clip(SkinTheme.shapes.image),
                    )
                }

                // (Nut play 72dp DA XOA 2026-08-03 — GIF tu chay lap, khong can bam)

                // Caption
                post.caption?.let { caption ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp) // Add padding to move it up from bottom
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = SkinTheme.shapes.sheet,
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
 * Man xem POST CU tu profile (bam o calendar) — feed chinh KHONG dung man nay
 * (PostScreen tu hien pager rieng tu 2026-07-26).
 * 2026-08-02 (lan 2): chuyen thanh VERTICAL PAGER giong het feed chinh — nhan
 * TOAN BO post cu (moi -> cu), mo dung post cua ngay vua bam, vuot len/xuong de
 * xem cac post khac (cung ngay roi den ngay khac); icon luoi mo GRID tong hop
 * tat ca post cu (bam 1 o -> ve pager dung post do); nut chup vien vang ve
 * camera; ⋯ = PostOptionsSheet Share/Download/Delete dung chung voi feed.
 * Back he thong: grid -> pager -> calendar.
 * [onDeleted]: profile truyen vao de dong man + refresh calendar sau khi xoa bai.
 */
@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PostDetailScreen(
    // TOAN BO post cu (moi -> cu) — pager vuot doc nhu feed chinh (2026-08-02:
    // truoc day chi nhan 1 post -> khong vuot xem duoc cac bai khac trong ngay)
    posts: List<Post>,
    // Post cua o calendar vua bam — pager mo dung trang nay
    initialPostId: String?,
    onBack: () -> Unit,
    navController: NavController,
    friends: List<User> = emptyList(),
    onDeleted: (() -> Unit)? = null,
    mainViewModel: MainViewModel = hiltViewModel(),
    postViewModel: PostViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // false = pager (mac dinh); true = GRID tong hop tat ca post cu (icon luoi)
    var showGrid by remember { mutableStateOf(false) }

    // Back he thong: dang o grid -> ve pager; dang o pager -> ve calendar profile
    BackHandler {
        if (showGrid) showGrid = false else onBack()
    }

    val initialPage = remember(posts, initialPostId) {
        posts.indexOfFirst { it.id == initialPostId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { posts.size })
    val currentPost = posts.getOrNull(pagerState.currentPage)

    // Menu ⋯ + xac nhan xoa (dong bo voi feed pager)
    var showOptions by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    val requestDownload = rememberGalleryDownloader()

    // Ve camera: pop ve entry camera co san (camera la startDestination)
    val goBackToCamera: () -> Unit = {
        if (!navController.popBackStack(Screen.Camera.route, false)) {
            navController.navigate(Screen.Camera.route) { launchSingleTop = true }
        }
    }

    // Reaction: emoji vua tha theo TUNG post (nhu feed) + emoji dang bay
    val selectedEmojiByPost = remember { mutableStateMapOf<String, String>() }
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
        // Khung anh: tai catalog 1 lan cho ca pager + grid
        postViewModel.loadFrames()
    }

    // Map frameId -> URL khung (nhu feed) de overlay len tung post
    val frames by postViewModel.frames.collectAsState()
    val frameUrls = remember(frames) {
        frames
            .mapNotNull { f -> f.imageUrl?.let { url -> f.frameId to url } }
            .toMap()
    }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                if (showGrid) {
                    // ==== GRID MODE: tong hop tat ca post cu (nhu grid o feed) ====
                    PostGrid(
                        posts = posts,
                        // Bam 1 o -> quay ve pager dung post do
                        onPostClick = { post ->
                            val index = posts.indexOfFirst { it.id == post.id }
                            if (index >= 0) {
                                scope.launch { pagerState.scrollToPage(index) }
                            }
                            showGrid = false
                        },
                        frameUrls = frameUrls,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    // ==== PAGER MODE: vuot len = post cu hon (bat dau tu ngay vua bam) ====
                    VerticalPager(
                        state = pagerState,
                        key = { index -> posts.getOrNull(index)?.id ?: index },
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val post = posts.getOrNull(page) ?: return@VerticalPager
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Chua cho hang nut day (overlay)
                                .padding(top = 16.dp, bottom = 150.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PostDetailContent(
                                post = post,
                                frameImageUrl = frameUrls[post.frameId],
                            )
                        }
                    }
                }
            }
        }

        // Emoji bay len tu thanh message roi mo dan
        FlyingEmojiOverlay(flyingEmojis)

        // Overlay day man hinh: pill nhan tin (chi bai nguoi khac) + hang nut
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Message Pill — AN voi bai cua CHINH MINH (fix 2026-07-27); currentUser
            // chua tai xong (id "unknown") cung an de khong loe pill tren bai minh
            if (!showGrid && currentPost != null && data.id != "unknown" && currentPost.user.id != data.id) {
                key(currentPost.id) {
                    MessageInputPill(
                        selectedEmoji = selectedEmojiByPost[currentPost.id],
                        onEmojiClick = { emoji ->
                            selectedEmojiByPost[currentPost.id] = emoji
                            postViewModel.react(currentPost.id, emoji)
                            flyingEmojis.add(newFlyingEmoji(emoji))
                        },
                        onSendMessage = { text ->
                            postViewModel.sendMessageToAuthor(currentPost, text)
                        },
                    )
                }
            }

            // Hang nut day — DONG BO voi pager feed: luoi | chup vien vang | ⋯
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Luoi: mo/dong grid tong hop cac post cu
                IconButton(onClick = { showGrid = !showGrid }) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "All posts",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Nut chup (ve camera) — cung style nut center 80dp vien vang nhu feed
                Circle(
                    outerSize = 80.dp,
                    gap = 7.dp,
                    backgroundColor = Color.Transparent,
                    borderColor = SkinTheme.colors.accent,
                    borderWidth = 3.dp,
                    onClick = goBackToCamera,
                )

                // ⋯ chi co nghia o pager (grid khong co "post hien tai")
                IconButton(
                    onClick = { showOptions = true },
                    enabled = !showGrid && currentPost != null,
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Post options",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }

    // ==== Menu ⋯ (dung chung voi feed): Share / Download / Delete (bai minh) / Cancel ====
    if (showOptions) {
        currentPost?.let { post ->
            val isVideo = post.postType == PostType.VIDEO
            PostOptionsSheet(
                isOwnPost = post.user.id == data.id,
                onShare = {
                    showOptions = false
                    scope.launch {
                        try {
                            MediaActions.share(context, post.thumbnailUrl, isVideo)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDownload = {
                    showOptions = false
                    requestDownload(post.thumbnailUrl, isVideo)
                },
                onDelete = {
                    showOptions = false
                    showConfirmDelete = true
                },
                onDismiss = { showOptions = false },
            )
        }
    }

    // Xac nhan xoa (huy duoc) — xoa that qua DELETE /moments/:id roi bao profile refresh
    if (showConfirmDelete) {
        currentPost?.let { post ->
            AlertDialog(
                onDismissRequest = { showConfirmDelete = false },
                title = { Text("Delete post?") },
                text = { Text("This moment will be permanently deleted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDelete = false
                            postViewModel.deleteMoment(post.id, onDeleted ?: onBack)
                        },
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDelete = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
