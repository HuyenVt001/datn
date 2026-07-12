package com.example.snapget.feature.post

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onGloballyPositioned
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
    val posts by mainViewModel.posts.collectAsState()
    val userPosts by mainViewModel.userPosts.collectAsState() // For specific user posts
    val friends by mainViewModel.friends.collectAsState()

    // Use ViewModel loading states instead of local loading state
    val postsLoading by mainViewModel.postsLoading.collectAsState()
    val userPostsLoading by mainViewModel.userPostsLoading.collectAsState()

    // Feed tu API server (nguon chinh cho tab Everyone)
    val postViewModel: PostViewModel = hiltViewModel()
    val apiFeed by postViewModel.feed.collectAsState()
    val feedStatus by postViewModel.feedStatus.collectAsState()

    // Sheet ban be (QR ket ban) — data tu API /friendships
    val friendsViewModel: FriendsViewModel = hiltViewModel()
    val apiFriends by friendsViewModel.friends.collectAsState()
    val apiFriendsStatus by friendsViewModel.friendsStatus.collectAsState()
    val inviteLink by friendsViewModel.inviteLink.collectAsState()
    var showFriendSheet by remember { mutableStateOf(false) }

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
                val user = (authState as AuthState.Authenticated).user
                mainViewModel.getAllPostsOfUserAndFriends(user)
                mainViewModel.fetchFriendsOfUser(user)
                mainViewModel.fetchCurrentUser()
                postViewModel.loadFeed() // feed tu server NestJS
            }

            else -> Log.d("PostScreen", "User is not authenticated, skipping post fetch")
        }
    }

    when {
        selectedPost != null -> {
            PostDetailScreen(
                post = selectedPost!!,
                onBack = { selectedPost = null },
                navController = navController,
                friends = friends,
            )
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                // Determine which posts to show and handle loading state
                // Tab Everyone doc tu API server; tab "you"/ban be van tu
                // Firestore cu — TODO(migrate): chuyen not sang API
                val everyonePosts = apiFeed.map { it.toPost(data, friends) }
                val (displayPosts, isCurrentlyLoading) = when (selectedUser?.id) {
                    "everyone" -> everyonePosts to (feedStatus is LoadStatus.Loading)
                    "you" -> userPosts to userPostsLoading
                    null -> everyonePosts to (feedStatus is LoadStatus.Loading)
                    else -> userPosts to userPostsLoading // Show friend's posts from userPosts StateFlow
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
                                text = when (selectedUser?.id) {
                                    "everyone" -> "No posts to show"
                                    "you" -> "You haven't posted anything yet"
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
                        PostGrid(
                            posts = displayPosts,
                            onPostClick = { post -> selectedPost = post },
                            modifier = Modifier.padding(top = (50 + 80).dp),
                        )
                    }
                }

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
                    onNotificationClick = { showNotifications = true },
                    onUserSelected = { user ->
                        selectedUser = user
                        // Null safety: only proceed if user is not null
                        user?.let { nonNullUser ->
                            // Fetch posts for selected user if it's not "everyone"
                            if (nonNullUser.id != "everyone" && nonNullUser.id != "you" && authState is AuthState.Authenticated) {
                                val currentUserId = (authState as AuthState.Authenticated).user.id
                                mainViewModel.getPostsForUser(nonNullUser.id, currentUserId)
                            } else if (nonNullUser.id == "you") {
                                // Fetch current user's posts
                                currentUser?.id?.let { userId ->
                                    mainViewModel.getPostsOfUser(userId)
                                }
                            } else if (authState is AuthState.Authenticated) {
                                // Fetch all posts
                                val authUser = (authState as AuthState.Authenticated).user
                                mainViewModel.getAllPostsOfUserAndFriends(authUser)
                            }
                        } ?: run {
                            // Handle case where user is null - default to showing all posts
                            if (authState is AuthState.Authenticated) {
                                val authUser = (authState as AuthState.Authenticated).user
                                mainViewModel.getAllPostsOfUserAndFriends(authUser)
                            }
                        }
                    },
                    // Hang "Add friends" cuoi dropdown -> mo sheet ban be
                    onAddFriendsClick = {
                        friendsViewModel.loadFriends()
                        friendsViewModel.loadInviteLink()
                        showFriendSheet = true
                    },
                    modifier = Modifier.align(Alignment.TopCenter).onGloballyPositioned { coordinates ->
                        // Get the width of the title in pixels
                        topbarHeight = coordinates.size.height
                    },
                )

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
