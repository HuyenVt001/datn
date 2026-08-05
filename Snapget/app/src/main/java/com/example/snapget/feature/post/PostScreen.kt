package com.example.snapget.feature.post

import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.sampleItems2
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.component.pill.MessageInputPill
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheet
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheetData
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.PostType
import com.example.snapget.core.model.User
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.network.dto.CoopInviteDto
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.MediaActions
import com.example.snapget.core.util.mapToUser
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.feature.coop.CoopViewModel
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Keo xuong qua nguong nay khi dang o POST MOI NHAT -> quay ve man camera
private val SWIPE_DOWN_TO_CAMERA_THRESHOLD = 120.dp

/**
 * Man feed (2026-07-26 — theo anh mau Locket):
 * - Mac dinh la PAGER doc full-screen: moi post 1 trang, moi nhat -> cu nhat;
 *   vuot len = post cu hon, dang o post moi nhat vuot xuong = ve camera.
 * - Bam icon luoi (goc duoi trai) -> xem GRID tong hop; bam 1 o -> quay ve pager
 *   dung post do.
 * - Menu ⋯ (goc duoi phai): Share / Download / Delete (chi bai minh) / Cancel.
 * - Thanh "Send message...": go text gui DM that toi tac gia, emoji tha reaction.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PostScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Feed tu API server — tab Everyone doc feed, tab You/ban be doc userMoments
    val postViewModel: PostViewModel = hiltViewModel()
    val apiFeed by postViewModel.feed.collectAsState()
    val feedStatus by postViewModel.feedStatus.collectAsState()
    val userMoments by postViewModel.userMoments.collectAsState()
    val userMomentsStatus by postViewModel.userMomentsStatus.collectAsState()
    val frames by postViewModel.frames.collectAsState()

    // Loi moi chup chung dang cho (banner tren feed) + dialog tra loi
    val coopViewModel: CoopViewModel = hiltViewModel()
    val pendingInvites by coopViewModel.pendingInvites.collectAsState()
    val coopError by coopViewModel.coopError.collectAsState()
    var coopInviteToRespond by remember { mutableStateOf<CoopInviteDto?>(null) }

    LaunchedEffect(coopError) {
        coopError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            coopViewModel.clearError()
        }
    }

    // Loi moi coop TTL chi 5 PHUT — poll nhe 10s/lan khi dang o feed de banner
    // hien kip (truoc chi load luc vao feed: ngoi xem feed la lo luon loi moi)
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (authState is AuthState.Authenticated) {
                coopViewModel.loadPending()
            }
        }
    }

    // Sheet ban be (QR ket ban) — data tu API /friendships
    val friendsViewModel: FriendsViewModel = hiltViewModel()
    val apiFriends by friendsViewModel.friends.collectAsState()
    val apiFriendsStatus by friendsViewModel.friendsStatus.collectAsState()
    val inviteLink by friendsViewModel.inviteLink.collectAsState()
    val friendRequests by friendsViewModel.requests.collectAsState()
    var showFriendSheet by remember { mutableStateOf(false) }

    // Ban be tu API — dung cho dropdown top bar + resolve tac gia moment
    val friendUsers = remember(apiFriends) {
        apiFriends.map { User(id = it.id, username = it.name, email = "", avatar = it.avatar) }
    }

    // false = pager full-screen (mac dinh khi vuot len tu camera); true = grid tong hop
    var showGrid by remember { mutableStateOf(false) }

    // Track the selected user for filtering posts
    var selectedUser by remember {
        mutableStateOf<User?>(
            User(
                id = "everyone",
                username = "Everyone",
                email = "",
                avatar = "",
            ),
        )
    }

    // Get current user from auth state
    val currentUser by mainViewModel.currentUser.collectAsState()
    val data = mapToUser(currentUser)

    // Fetch posts and friends when authenticated
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                mainViewModel.fetchCurrentUser()
                postViewModel.loadFeed() // feed tu server NestJS
                postViewModel.loadFrames() // catalog khung (overlay len anh)
                coopViewModel.loadPending() // loi moi chup chung dang cho
                friendsViewModel.loadFriends() // dropdown top bar + resolve tac gia
                friendsViewModel.loadRequests() // banner 💌 loi moi ket ban dang cho
            }

            else -> Log.d("PostScreen", "User is not authenticated, skipping post fetch")
        }
    }

    // Tat ca tab deu doc tu API server: Everyone = /moments/feed,
    // You = /moments/mine, ban = /moments/user/:uid
    val everyonePosts = remember(apiFeed, data, friendUsers) {
        apiFeed.map { it.toPost(data, friendUsers) }
    }
    val tabPosts = remember(userMoments, data, friendUsers) {
        userMoments.map { it.toPost(data, friendUsers) }
    }
    val isEveryoneTab = selectedUser == null || selectedUser?.id == "everyone"
    val (displayPosts, isCurrentlyLoading) = if (isEveryoneTab) {
        everyonePosts to (feedStatus is LoadStatus.Loading)
    } else {
        tabPosts to (userMomentsStatus is LoadStatus.Loading)
    }

    val frameUrls = remember(frames) {
        frames
            .mapNotNull { f -> f.imageUrl?.let { url -> f.frameId to url } }
            .toMap()
    }

    // ==== Pager state (moi nhat = trang 0; server da sort postTime desc) ====
    val pagerState = rememberPagerState(pageCount = { displayPosts.size })
    val currentPost = displayPosts.getOrNull(pagerState.currentPage)
    val myId = data?.id?.takeIf { it != "unknown" }
    // Doi tab (Everyone/You/ban) -> quay ve post moi nhat cua tab do
    LaunchedEffect(selectedUser?.id) {
        if (displayPosts.isNotEmpty()) pagerState.scrollToPage(0)
    }

    // Business rule "feed da xem": mark seen khi 1 post THUC SU dung lai tren
    // man hinh pager (settledPage) — khong mark bai cua chinh minh
    LaunchedEffect(pagerState.settledPage, displayPosts, myId, showGrid) {
        if (!showGrid) {
            displayPosts.getOrNull(pagerState.settledPage)?.let { post ->
                if (myId != null && post.user.id != myId) {
                    postViewModel.markSeen(post.id)
                }
            }
        }
    }

    // Reaction: emoji vua tha theo TUNG post + emoji dang bay
    val selectedEmojiByPost = remember { mutableStateMapOf<String, String>() }
    val flyingEmojis = remember { mutableStateListOf<FlyingEmoji>() }

    // Menu ⋯ + xac nhan xoa
    var optionsPost by remember { mutableStateOf<Post?>(null) }
    var confirmDeletePost by remember { mutableStateOf<Post?>(null) }

    // Toast one-shot cho xoa bai / gui tin nhan
    val actionMessage by postViewModel.actionMessage.collectAsState()
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            postViewModel.clearActionMessage()
        }
    }

    // Ve camera: pop ve entry camera co san (camera la startDestination)
    val goBackToCamera: () -> Unit = {
        if (!navController.popBackStack(Screen.Camera.route, false)) {
            navController.navigate(Screen.Camera.route) { launchSingleTop = true }
        }
    }

    // Download ve thu vien (helper dung chung voi PostDetailScreen — 2026-08-02)
    val requestDownload = rememberGalleryDownloader()

    // Dang o POST MOI NHAT (trang 0) ma keo xuong tiep -> pager khong cuon duoc
    // nua, phan du roi vao onPostScroll -> cong don, qua nguong thi ve camera
    val backThresholdPx = with(LocalDensity.current) { SWIPE_DOWN_TO_CAMERA_THRESHOLD.toPx() }
    var pullDownTotal by remember { mutableFloatStateOf(0f) }
    val backToCameraConnection = remember(pagerState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f && pagerState.currentPage == 0) {
                    pullDownTotal += available.y
                    if (pullDownTotal > backThresholdPx) {
                        pullDownTotal = 0f
                        goBackToCamera()
                    }
                } else if (available.y < 0f) {
                    pullDownTotal = 0f
                }
                return Offset.Zero
            }

            // Het gesture (tha tay/fling) -> reset: khong cong don nhieu cu keo
            // nhe roi rac thanh 1 lan "vuot ve camera" bat ngo (fix 2026-07-26)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pullDownTotal = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showGrid) {
            // ==== GRID MODE: tong hop tat ca post (bam icon luoi tu pager) ====
            when {
                isCurrentlyLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                displayPosts.isEmpty() -> {
                    EmptyFeedMessage(
                        isEveryoneTab = isEveryoneTab,
                        userMomentsStatus = userMomentsStatus,
                        selectedUser = selectedUser,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    PostGrid(
                        posts = displayPosts,
                        // Bam 1 o -> quay ve pager dung post do
                        onPostClick = { post ->
                            val index = displayPosts.indexOfFirst { it.id == post.id }
                            if (index >= 0) {
                                scope.launch { pagerState.scrollToPage(index) }
                            }
                            showGrid = false
                        },
                        modifier = Modifier.padding(top = (50 + 80).dp),
                        frameUrls = frameUrls,
                        // Mark-seen khi o anh hien len (khong mark bai cua CHINH MINH)
                        onPostVisible = { post ->
                            if (myId != null && post.user.id != myId) {
                                postViewModel.markSeen(post.id)
                            }
                        },
                    )
                }
            }

            MainBottomBar(
                navController = navController,
                // Center = VE camera bang pop (goBackToCamera) — navigate tran se
                // day chong Camera/Post len back stack moi lan bam (fix 2026-07-26)
                items = sampleItems2.map { if (it.isCenter) it.copy(onClick = goBackToCamera) else it },
                iconTint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            // ==== PAGER MODE (mac dinh): moi post 1 trang, vuot doc ====
            when {
                isCurrentlyLoading && displayPosts.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                displayPosts.isEmpty() -> {
                    // Van cho vuot xuong de ve camera du khong co bai nao
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                var dragTotal = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { dragTotal = 0f },
                                    onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                                    onDragEnd = {
                                        if (dragTotal > backThresholdPx) goBackToCamera()
                                    },
                                )
                            },
                    ) {
                        EmptyFeedMessage(
                            isEveryoneTab = isEveryoneTab,
                            userMomentsStatus = userMomentsStatus,
                            selectedUser = selectedUser,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                else -> {
                    VerticalPager(
                        state = pagerState,
                        key = { index -> displayPosts.getOrNull(index)?.id ?: index },
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(backToCameraConnection),
                    ) { page ->
                        // getOrNull: xoa bai cuoi lam list co lai trong khi pager con
                        // render page cu -> index truc tiep se IndexOutOfBounds
                        val post = displayPosts.getOrNull(page) ?: return@VerticalPager
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Chua cho top bar (tren) + pill/bottom row (duoi)
                                .padding(top = 110.dp, bottom = 210.dp),
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

            // Thanh message + hang nut day man hinh (co dinh, khong cuon theo trang).
            // Ban phim mo -> CHI o nhap noi len (imePadding), hang nut chup/luoi/⋯
            // AN di thay vi bi keo len theo (fix 2026-07-27)
            val imeVisible = WindowInsets.isImeVisible
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                // AN thanh react + reply voi bai cua CHINH MINH (fix 2026-07-27) —
                // tu react/tu nhan tin cho minh vo nghia (truoc chi toast bao loi).
                // myId null (currentUser chua tai) cung an de khong loe pill tren bai minh
                if (currentPost != null && myId != null && currentPost.user.id != myId) {
                    // key(post.id): text go do trong pill KHONG dinh sang bai khac
                    // khi luot trang (fix 2026-07-26 — truoc day de gui nham nguoi)
                    key(currentPost.id) {
                        MessageInputPill(
                            selectedEmoji = selectedEmojiByPost[currentPost.id],
                            onEmojiClick = { emoji ->
                                selectedEmojiByPost[currentPost.id] = emoji
                                postViewModel.react(currentPost.id, emoji)
                                flyingEmojis.add(newFlyingEmoji(emoji))
                            },
                            onSendMessage = { text ->
                                // Reply gui KEM anh/video cua bai (attachment)
                                postViewModel.sendMessageToAuthor(currentPost, text)
                            },
                        )
                    }
                }

                // Hang duoi cung (khop mau): luoi | nut chup trang vien vang | ⋯
                if (!imeVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showGrid = true }) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "All posts",
                                // Theo theme (fix 2026-07-26): hardcode trang la vo hinh o Light mode
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        // Nut chup (ve camera) — cung style nut center 80dp vien vang
                        Circle(
                            outerSize = 80.dp,
                            gap = 7.dp,
                            backgroundColor = Color.Transparent,
                            borderColor = SkinTheme.colors.accent,
                            borderWidth = 3.dp,
                            onClick = goBackToCamera,
                        )

                        IconButton(
                            onClick = { currentPost?.let { optionsPost = it } },
                            enabled = currentPost != null,
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        MainTopBar(
            navController = navController,
            user = data,
            friends = friendUsers,
            onMessageClick = { navController.navigate(Screen.Message.route) },
            onProfileClick = {
                data?.id?.let { userId ->
                    navController.navigate("profile?userId=$userId")
                } ?: navController.navigate("profile")
            },
            onUserSelected = { user ->
                selectedUser = user
                // Doi tab -> tai tu API: You = /moments/mine,
                // ban be = /moments/user/:uid, Everyone = refresh feed
                when (user?.id) {
                    null, "everyone" -> postViewModel.loadFeed()
                    "you" -> postViewModel.loadUserMoments(null)
                    else -> postViewModel.loadUserMoments(user.id)
                }
            },
            // Hang "Add friends" cuoi dropdown -> mo sheet ban be
            onAddFriendsClick = {
                friendsViewModel.loadFriends()
                friendsViewModel.loadInviteLink()
                friendsViewModel.loadRequests() // loi moi dang cho minh xac nhan
                showFriendSheet = true
            },
            // Nut cup 🏆 -> man Daily Quest (entry chot 2026-07-13)
            onQuestClick = { navController.navigate(Screen.DailyQuest.route) },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Banner thong bao tren feed: loi moi CHUP CHUNG + loi moi KET BAN dang cho
        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 140.dp, start = 16.dp, end = 16.dp),
        ) {
            // Loi moi chup chung — cham de mo dialog Accept/Decline (redesign 2026-08-02)
            pendingInvites.firstOrNull()?.let { invite ->
                Surface(
                    shape = SkinTheme.shapes.image,
                    color = SkinTheme.colors.accent,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable { coopInviteToRespond = invite },
                ) {
                    Text(
                        text = buildString {
                            append("📸 ")
                            append(invite.inviterName ?: "A friend")
                            append(" invited you to a co-op capture!")
                            if (pendingInvites.size > 1) {
                                append(" (+${pendingInvites.size - 1})")
                            }
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // Loi moi ket ban dang cho — cham de mo sheet ban be (section 💌) xac nhan.
            // Hien ca khi app dang mo (FCM chi bao khi o ngoai app) — fix 2026-07-27
            friendRequests.firstOrNull()?.let { request ->
                Surface(
                    shape = SkinTheme.shapes.image,
                    color = SkinTheme.colors.accent,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable {
                        friendsViewModel.loadFriends()
                        friendsViewModel.loadInviteLink()
                        friendsViewModel.loadRequests()
                        showFriendSheet = true
                    },
                ) {
                    Text(
                        text = buildString {
                            append("💌 ")
                            append(request.name)
                            append(" sent you a friend request!")
                            if (friendRequests.size > 1) {
                                append(" (+${friendRequests.size - 1})")
                            }
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // Emoji bay len khi tha reaction (pager mode)
        FlyingEmojiOverlay(flyingEmojis)

        // Sheet quan ly ban be: dem ban, QR ket ban, xoa ban (xac nhan trong sheet)
        UserDetailBottomSheet(
            data = if (showFriendSheet) {
                UserDetailBottomSheetData(
                    friends = apiFriends,
                    isLoading = apiFriendsStatus is LoadStatus.Loading,
                    inviteCode = inviteLink?.inviteCode,
                    inviteLink = inviteLink?.link,
                    inviteExpiresAt = inviteLink?.expiresAt,
                    requests = friendRequests,
                    onAcceptRequest = { request -> friendsViewModel.acceptRequest(request.id) },
                    onDeclineRequest = { request -> friendsViewModel.declineRequest(request.id) },
                    onScanQrClick = {
                        showFriendSheet = false
                        navController.navigate(Screen.QrScan.route)
                    },
                    onRemoveFriend = { friend -> friendsViewModel.removeFriend(friend.id) },
                )
            } else {
                null
            },
            onDismiss = { showFriendSheet = false },
        )
    }

    // ==== Dialog tra loi loi moi CHUP CHUNG: Accept -> man chup coop; Decline ====
    coopInviteToRespond?.let { invite ->
        val inviterName = invite.inviterName ?: "A friend"
        AlertDialog(
            onDismissRequest = { coopInviteToRespond = null },
            title = { Text("Co-op capture invite") },
            text = {
                Text("$inviterName invited you to take a photo together. Invites expire after 5 minutes.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coopInviteToRespond = null
                        coopViewModel.acceptInvite(invite.inviteId) {
                            navController.navigate(
                                Screen.CoopCapture.route +
                                    "?inviteId=" + invite.inviteId +
                                    "&name=" + Uri.encode(inviterName),
                            )
                        }
                    },
                ) {
                    Text("Accept")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        coopViewModel.declineInvite(invite.inviteId)
                        coopInviteToRespond = null
                    },
                ) {
                    Text("Decline", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    // ==== Menu ⋯ (khop anh mau): Share / Download / Delete (bai minh) / Cancel ====
    optionsPost?.let { post ->
        val isVideo = post.postType == PostType.VIDEO
        PostOptionsSheet(
            isOwnPost = post.user.id == myId,
            onShare = {
                optionsPost = null
                scope.launch {
                    try {
                        MediaActions.share(context, post.thumbnailUrl, isVideo)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Share failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDownload = {
                optionsPost = null
                requestDownload(post.thumbnailUrl, isVideo)
            },
            onDelete = {
                optionsPost = null
                confirmDeletePost = post
            },
            onDismiss = { optionsPost = null },
        )
    }

    // Xac nhan xoa (huy duoc) — xoa that qua DELETE /moments/:id
    confirmDeletePost?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmDeletePost = null },
            title = { Text("Delete post?") },
            text = { Text("This moment will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        postViewModel.deleteMoment(post.id)
                        confirmDeletePost = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePost = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Trang thai feed rong (dung chung pager + grid). */
@Composable
private fun EmptyFeedMessage(
    isEveryoneTab: Boolean,
    userMomentsStatus: LoadStatus,
    selectedUser: User?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when {
                // Tab Everyone KHONG duoc hien loi dinh lai tu tab khac
                isEveryoneTab -> "No posts to show"
                // Loi tai tab (vd 403 cua server) -> hien message truc tiep
                userMomentsStatus is LoadStatus.Error -> userMomentsStatus.error
                selectedUser?.id == "you" -> "You haven't posted anything yet"
                else -> "${selectedUser?.username ?: "This user"} hasn't posted anything yet"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
        if (selectedUser?.id == "you") {
            Text(
                text = "Tap the camera button to create your first post",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * Bottom sheet menu ⋯ cua 1 post (khop anh mau popup):
 * Share / Download / Delete (do, CHI bai cua minh) / Cancel.
 * (Report BO theo quyet dinh user 2026-07-26 — server khong co he thong report.)
 * Dung chung: feed pager + PostDetailScreen (xem post cu tu profile — 2026-08-02).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostOptionsSheet(
    isOwnPost: Boolean,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            SheetActionRow(label = "Share", onClick = onShare)
            SheetActionRow(label = "Download", onClick = onDownload)
            if (isOwnPost) {
                SheetActionRow(
                    label = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
            SheetActionRow(label = "Cancel", onClick = onDismiss)
        }
    }
}

/** 1 hang hanh dong trong sheet menu — chu can giua, full width. */
@Composable
private fun SheetActionRow(
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
    )
}
