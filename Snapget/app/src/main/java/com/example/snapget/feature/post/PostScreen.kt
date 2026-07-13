package com.example.snapget.feature.post

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.sampleItems2
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheet
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheetData
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.User
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.mapToUser
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.feature.coop.CoopViewModel
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.feature.post.PostDetailScreen
import com.example.snapget.navigation.Screen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PostScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsState()

    // Feed tu API server — tab Everyone doc feed, tab You/ban be doc userMoments
    val postViewModel: PostViewModel = hiltViewModel()
    val apiFeed by postViewModel.feed.collectAsState()
    val feedStatus by postViewModel.feedStatus.collectAsState()
    val userMoments by postViewModel.userMoments.collectAsState()
    val userMomentsStatus by postViewModel.userMomentsStatus.collectAsState()
    val frames by postViewModel.frames.collectAsState()

    // Loi moi chup chung dang cho (banner tren feed)
    val coopViewModel: CoopViewModel = hiltViewModel()
    val pendingInvites by coopViewModel.pendingInvites.collectAsState()

    // Sheet ban be (QR ket ban) — data tu API /friendships
    val friendsViewModel: FriendsViewModel = hiltViewModel()
    val apiFriends by friendsViewModel.friends.collectAsState()
    val apiFriendsStatus by friendsViewModel.friendsStatus.collectAsState()
    val inviteLink by friendsViewModel.inviteLink.collectAsState()
    var showFriendSheet by remember { mutableStateOf(false) }

    // Ban be tu API — dung cho dropdown top bar + resolve tac gia moment
    // (remember de khong re-map moi lan recompose)
    val friendUsers = remember(apiFriends) {
        apiFriends.map { User(id = it.id, username = it.name, email = "", avatar = it.avatar) }
    }

    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var showNotifications by remember { mutableStateOf(false) }

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
                Log.d("UserDebug", "Auth State: Authenticated")
                mainViewModel.fetchCurrentUser()
                postViewModel.loadFeed() // feed tu server NestJS
                postViewModel.loadFrames() // catalog khung (overlay len o anh trong feed)
                coopViewModel.loadPending() // loi moi chup chung dang cho
                friendsViewModel.loadFriends() // dropdown top bar + resolve tac gia
            }

            else -> Log.d("PostScreen", "User is not authenticated, skipping post fetch")
        }
    }

    // Business rule "feed da xem": mark-seen chuyen xuong PostGrid.onPostVisible —
    // chi danh dau moment THUC SU hien len man hinh khi luot (dung spec "luot qua"),
    // khong con mark ca feed ngay khi load nhu truoc (ghi view cho bai chua ai xem).

    when {
        selectedPost != null -> {
            PostDetailScreen(
                post = selectedPost!!,
                onBack = { selectedPost = null },
                navController = navController,
                friends = friendUsers,
            )
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                // Tat ca tab deu doc tu API server (bo Firestore truc tiep 2026-07-13):
                // Everyone = /moments/feed, You = /moments/mine, ban = /moments/user/:uid
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

                var topbarHeight by remember { mutableIntStateOf(0) }

                when {
                    isCurrentlyLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    displayPosts.isEmpty() -> {
                        // Show empty state
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = when {
                                    // Tab Everyone KHONG duoc hien loi dinh lai tu tab khac
                                    isEveryoneTab -> "No posts to show"
                                    // Loi tai tab (vd 403 cua server) -> hien message truc tiep
                                    userMomentsStatus is LoadStatus.Error ->
                                        (userMomentsStatus as LoadStatus.Error).error
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
                    else -> {
                        val frameUrls = remember(frames) {
                            frames
                                .mapNotNull { f -> f.imageUrl?.let { url -> f.frameId to url } }
                                .toMap()
                        }
                        PostGrid(
                            posts = displayPosts,
                            onPostClick = { post -> selectedPost = post },
                            modifier = Modifier.padding(top = (50 + 80).dp),
                            frameUrls = frameUrls,
                            // Mark-seen khi o anh hien len (khong mark bai cua CHINH MINH).
                            // Chi mark khi DA biet minh la ai — mapToUser(null) tra ve
                            // id "unknown" luc currentUser chua load, so sanh luc do se
                            // mark nham ca bai cua minh (seenOnce ghi nho, khong sua lai duoc).
                            onPostVisible = { post ->
                                val myId = data?.id
                                if (myId != null && myId != "unknown" && post.user.id != myId) {
                                    postViewModel.markSeen(post.id)
                                }
                            },
                        )
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
                    onNotificationClick = { showNotifications = true },
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
                        showFriendSheet = true
                    },
                    // Nut cup 🏆 -> man Daily Quest (entry chot 2026-07-13)
                    onQuestClick = { navController.navigate(Screen.DailyQuest.route) },
                    modifier = Modifier.align(Alignment.TopCenter).onGloballyPositioned { coordinates ->
                        // Get the width of the title in pixels
                        topbarHeight = coordinates.size.height
                    },
                )

                // Banner loi moi CHUP CHUNG dang cho — cham de mo man chap nhan
                pendingInvites.firstOrNull()?.let { invite ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Yellow,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 140.dp, start = 16.dp, end = 16.dp)
                            .clickable {
                                navController.navigate(
                                    Screen.CoopAccept.route +
                                        "?inviteId=" + invite.inviteId +
                                        "&mediaUrl=" + Uri.encode(invite.inviterMediaUrl) +
                                        "&name=" + Uri.encode(invite.inviterName ?: "friend"),
                                )
                            },
                    ) {
                        Text(
                            text = buildString {
                                append("📸 ")
                                append(invite.inviterName ?: "Ban be")
                                append(" moi ban chup chung!")
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

                MainBottomBar(
                    navController = navController,
                    items = sampleItems2,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // Sheet quan ly ban be: dem ban, QR ket ban, xoa ban (xac nhan trong sheet)
                UserDetailBottomSheet(
                    data = if (showFriendSheet) {
                        UserDetailBottomSheetData(
                            friends = apiFriends,
                            isLoading = apiFriendsStatus is LoadStatus.Loading,
                            inviteCode = inviteLink?.inviteCode,
                            inviteLink = inviteLink?.link,
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
        }
    }
}
