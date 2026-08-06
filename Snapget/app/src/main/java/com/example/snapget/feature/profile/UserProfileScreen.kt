package com.example.snapget.feature.profile

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.constants.Month
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.ImageSetting
import com.example.snapget.core.designsystem.component.empty.EmptyDayItem
import com.example.snapget.core.designsystem.component.grid.PostGridItemWithBadge
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheet
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheetData
import com.example.snapget.core.designsystem.component.topbar.UserProfileTopBar
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.User
import com.example.snapget.core.network.dto.InviteLinkDto
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.calculateDaysOfMonthInYear
import com.example.snapget.core.util.copyUriToCacheFile
import com.example.snapget.core.util.groupPostsByDay
import com.example.snapget.core.util.groupPostsByMonthYear
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.feature.post.PostDetailScreen
import com.example.snapget.feature.post.toPost
import com.example.snapget.navigation.Screen
import java.io.File
import java.time.LocalDate

data class MonthPosts(
    val month: Month,
    val year: Int,
    val posts: List<Post>,
)

data class DayPostGroup(
    val dayNumber: Int,
    val posts: List<Post>,
) {
    val count: Int get() = posts.size
    val hasMultiplePosts: Boolean get() = posts.size > 1
    val primaryPost: Post? get() = posts.firstOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UserProfile(
    navController: NavController,
    userId: String? = null,
    friendsViewModel: FriendsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    // Ho so + moment tu API (ProfileViewModel) — thay Firestore cu 2026-07-12
    val profile by profileViewModel.profile.collectAsState()
    val moments by profileViewModel.moments.collectAsState()
    val profileStatus by profileViewModel.status.collectAsState()
    val updateStatus by profileViewModel.updateStatus.collectAsState()

    LaunchedEffect(userId) {
        profileViewModel.load(userId)
    }

    val apiFriends by friendsViewModel.friends.collectAsState()
    val apiFriendsStatus by friendsViewModel.friendsStatus.collectAsState()
    val inviteLink by friendsViewModel.inviteLink.collectAsState()
    val friendRequests by friendsViewModel.requests.collectAsState()

    UserProfileContent(
        profile = profile,
        moments = moments,
        profileStatus = profileStatus,
        updateStatus = updateStatus,
        apiFriends = apiFriends,
        apiFriendsStatus = apiFriendsStatus,
        inviteLink = inviteLink,
        friendRequests = friendRequests,
        navController = navController,
        onLoadFriends = {
            friendsViewModel.loadFriends()
            friendsViewModel.loadInviteLink()
            friendsViewModel.loadRequests() // loi moi dang cho minh xac nhan
        },
        onAcceptRequest = { request -> friendsViewModel.acceptRequest(request.id) },
        onDeclineRequest = { request -> friendsViewModel.declineRequest(request.id) },
        onRemoveFriend = { friend -> friendsViewModel.removeFriend(friend.id) },
        onUpdateProfile = { newName, avatarFile ->
            profileViewModel.updateProfile(newName, avatarFile)
        },
        onResetUpdateStatus = { profileViewModel.resetUpdateStatus() },
        // Xoa bai tu man detail -> tai lai moment de calendar het o ngay da xoa
        onReloadProfile = { profileViewModel.load(userId) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UserProfileContent(
    profile: ProfileUi?,
    moments: List<MomentDto>,
    profileStatus: LoadStatus,
    updateStatus: LoadStatus,
    apiFriends: List<FriendUi>,
    apiFriendsStatus: LoadStatus,
    inviteLink: InviteLinkDto?,
    navController: NavController,
    onLoadFriends: () -> Unit,
    onRemoveFriend: (FriendUi) -> Unit,
    // Loi moi ket ban dang cho minh xac nhan (default rong de preview khong can truyen)
    friendRequests: List<FriendUi> = emptyList(),
    onAcceptRequest: (FriendUi) -> Unit = {},
    onDeclineRequest: (FriendUi) -> Unit = {},
    onUpdateProfile: (String, File?) -> Unit,
    onResetUpdateStatus: () -> Unit,
    onReloadProfile: () -> Unit = {},
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(updateStatus) {
        when (val status = updateStatus) {
            is LoadStatus.Success -> {
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                showEditDialog = false
                onResetUpdateStatus()
            }
            is LoadStatus.Error -> {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                onResetUpdateStatus()
            }
            else -> Unit
        }
    }

    // Map MomentDto -> Post cho calendar + detail (tac gia = chu profile)
    val ownerUser = profile?.let {
        User(id = it.uid, username = it.name, email = it.email ?: "", avatar = it.avatar)
    }
    val posts = moments.map { it.toPost(ownerUser, emptyList()) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }

    // Group posts by month and year
    val groupedPosts = groupPostsByMonthYear(posts)

    // Sheet ban be — data that tu API /friendships (FriendsViewModel)
    var showFriendSheet by remember { mutableStateOf(false) }

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
                onAcceptRequest = onAcceptRequest,
                onDeclineRequest = onDeclineRequest,
                onScanQrClick = {
                    showFriendSheet = false
                    navController.navigate(Screen.QrScan.route)
                },
                onRemoveFriend = onRemoveFriend,
            )
        } else {
            null
        },
        onDismiss = { showFriendSheet = false },
    )

    when {
        selectedPost != null -> {
            PostDetailScreen(
                // TOAN BO post cu (moi -> cu) — pager vuot doc nhu feed, mo dung
                // post cua ngay vua bam; icon luoi hien grid tong hop (2026-08-02)
                posts = posts.sortedByDescending { it.createdAt },
                initialPostId = selectedPost!!.id,
                onBack = { selectedPost = null },
                navController = navController,
                // Xoa bai thanh cong -> toast + dong detail + tai lai calendar
                onDeleted = {
                    Toast.makeText(context, "Post deleted.", Toast.LENGTH_SHORT).show()
                    selectedPost = null
                    onReloadProfile()
                },
            )
        }

        profileStatus is LoadStatus.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        profile == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Loi nghiep vu (vd 403 "Chi xem duoc moment cua ban be.") hien truc tiep
                Text((profileStatus as? LoadStatus.Error)?.error ?: "User not found")
            }
        }

        else -> {
            val profileData = profile!!
            Log.d("UserProfile", "Displaying profile for user: ${profileData.name}")

            Scaffold(
                topBar = {
                    UserProfileTopBar(
                        navController = navController,
                        onFriendsClick = {
                            onLoadFriends()
                            showFriendSheet = true
                        },
                    )
                },
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Fixed ProfileHeader - never scrolls
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background,
                            shadowElevation = 8.dp,
                        ) {
                            ProfileHeader(
                                name = profileData.name,
                                email = profileData.email,
                                avatar = profileData.avatar,
                                // Chi ho so cua MINH moi sua duoc
                                onEditClick = if (profileData.isSelf) {
                                    { showEditDialog = true }
                                } else {
                                    null
                                },
                                modifier = Modifier.padding(16.dp),
                            )
                        }

                        // Scrollable content area
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                        ) {
                            // Scrollable posts content
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 120.dp, // Space for fixed stats
                                ),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                items(groupedPosts.reversed()) { monthPosts ->
                                    MonthSection(
                                        monthPosts = monthPosts,
                                        onPostClick = { post -> selectedPost = post },
                                    )
                                }
                            }

                            // Fixed ProfileStats at bottom - completely independent
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background,
                                shadowElevation = 8.dp,
                            ) {
                                Column {
                                    // Gradient fade
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(16.dp)
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background,
                                                    ),
                                                ),
                                            ),
                                    )

                                    // Stats component — data that; profile nguoi khac CHI hien streak
                                    ProfileStats(
                                        momentCount = posts.size,
                                        streakDays = profileData.personalStreak,
                                        showMoments = profileData.isSelf,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dialog sua ho so: doi ten + chon avatar tu thu vien
            if (showEditDialog) {
                EditProfileDialog(
                    currentName = profileData.name,
                    currentAvatar = profileData.avatar,
                    isSaving = updateStatus is LoadStatus.Loading,
                    onSave = onUpdateProfile,
                    onDismiss = { showEditDialog = false },
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    email: String?,
    avatar: String,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f), // Take available space
        ) {
            // Username + nut sua (chi ho so cua minh)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (onEditClick != null) {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Email chi co khi xem profile cua MINH (server giau email nguoi khac)
            if (email != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "@$email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Profile Picture
        Circle(
            outerSize = 100.dp,
            gap = 5.dp,
            backgroundColor = SkinTheme.colors.pill,
            borderColor = SkinTheme.colors.accentGold,
            onClick = { onEditClick?.invoke() },
            imageSetting = ImageSetting(
                imageUrl = avatarOrDefault(avatar, name),
                contentDescription = "Profile picture",
            ),
        )
    }
}

/**
 * Dialog sua ho so: doi ten hien thi (<=30 ky tu) + chon avatar tu thu vien.
 * Avatar chon xong duoc copy vao cache roi upload khi bam Luu.
 */
@Composable
private fun EditProfileDialog(
    currentName: String,
    currentAvatar: String,
    isSaving: Boolean,
    onSave: (String, File?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentName) }
    var avatarFile by remember { mutableStateOf<File?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { avatarFile = copyUriToCacheFile(context, it, "avatar") }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = SkinTheme.colors.surface,
        title = {
            Text(text = "Edit profile", color = SkinTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Avatar: cham de chon anh moi tu thu vien
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(SkinTheme.colors.pill)
                        .clickable(enabled = !isSaving) { pickImage.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    val model: Any? = avatarFile ?: currentAvatar.takeIf { it.isNotEmpty() }
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Pick avatar",
                            tint = SkinTheme.colors.textPrimary,
                        )
                    }
                }
                Text(
                    text = "Tap the photo to change your avatar",
                    color = SkinTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, avatarFile) },
                enabled = !isSaving && name.isNotBlank(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SkinTheme.colors.accent,
                    )
                } else {
                    Text(text = "Save", color = SkinTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = "Cancel", color = SkinTheme.colors.textPrimary)
            }
        },
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ProfileStats(
    momentCount: Int,
    streakDays: Int,
    showMoments: Boolean, // false khi xem profile nguoi khac (chi hien streak)
    modifier: Modifier = Modifier,
) {
    val goldColors = listOf(
        SkinTheme.colors.accentGold, // Gold
        Color(0xFFFFA500), // Orange Gold
        Color(0xFFFFE55C), // Light Gold
        SkinTheme.colors.accentGold, // Gold
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Main Stats Row
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(goldColors),
                        shape = SkinTheme.shapes.pill,
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x40FFD700),
                                Color(0x20FFA500),
                            ),
                        ),
                        shape = SkinTheme.shapes.pill,
                    )
                    .clip(SkinTheme.shapes.pill)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showMoments) {
                        StatItem(
                            icon = "🧡",
                            count = "$momentCount",
                            label = "Moments",
                        )

                        VerticalDivider(
                            color = SkinTheme.colors.surface,
                            modifier = Modifier.height(20.dp),
                        )
                    }

                    StatItem(
                        icon = "🔥",
                        count = "${streakDays}d",
                        label = "streak",
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: String,
    count: String,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
        )
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MonthSection(
    monthPosts: MonthPosts,
    onPostClick: (Post) -> Unit,
) {
    val topRounded = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val bottomRounded = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(bottomRounded)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        // Month header

        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            // Layer 1: Gradient background with blur
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(topRounded)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC424242), // semi-transparent dark gray
                                Color(0xCC616161), // semi-transparent medium gray
                            ),
                        ),
                    )
                    .blur(16.dp), // only blurs the background layer
            )

            // Layer 2: Text on top
            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .clip(topRounded)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "${monthPosts.month.displayName} ${monthPosts.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        HorizontalDivider(
            color = SkinTheme.colors.surface,
            modifier = Modifier.height(10.dp),
        )

        // Calendar Grid for this month
        MonthCalendarGrid(
            monthPosts = monthPosts,
            onPostClick = onPostClick,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MonthCalendarGrid(
    monthPosts: MonthPosts,
    onPostClick: (Post) -> Unit,
) {
    val daysInMonth = calculateDaysOfMonthInYear(monthPosts.month, monthPosts.year)
    val postsByDay = groupPostsByDay(monthPosts.posts, daysInMonth)

    // Get the first day of the month and calculate offset
    val firstDayOfMonth = LocalDate.of(monthPosts.year, monthPosts.month.ordinal + 1, 1)
    val startDayOffset = (firstDayOfMonth.dayOfWeek.value - 1) % 7 // Monday = 0, Sunday = 6

    // Calculate total cells needed (offset + days in month)
    val totalCells = startDayOffset + daysInMonth
    val rows = (totalCells + 6) / 7 // Round up to get number of rows

    Log.d("Calendar", "Month: ${monthPosts.month.displayName} ${monthPosts.year}")
    Log.d("Calendar", "First day: ${firstDayOfMonth.dayOfWeek}, Offset: $startDayOffset")
    Log.d("Calendar", "Days in month: $daysInMonth, Total cells: $totalCells, Rows: $rows")

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = SkinTheme.shapes.image,
            )
            .padding(16.dp),
    ) {
        // Day headers (Mon, Tue, Wed, etc.)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val dayHeaders = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            dayHeaders.forEach { dayHeader ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = dayHeader,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Calendar grid rows
        repeat(rows) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(7) { columnIndex ->
                    val cellIndex = rowIndex * 7 + columnIndex

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(65.dp),
                    ) {
                        when {
                            // Empty cell before month starts
                            cellIndex < startDayOffset -> {
                                // Invisible placeholder to maintain grid structure
                                Box(modifier = Modifier.fillMaxSize())
                            }
                            // Days within the month
                            cellIndex < startDayOffset + daysInMonth -> {
                                val dayNumber = cellIndex - startDayOffset + 1
                                val dayPostGroup = postsByDay[dayNumber]

                                if (dayPostGroup != null && dayPostGroup.posts.isNotEmpty()) {
                                    PostGridItemWithBadge(
                                        dayPostGroup = dayPostGroup,
                                        onClick = { onPostClick(dayPostGroup.primaryPost!!) },
                                    )
                                } else {
                                    EmptyDayItem(dayNumber = dayNumber)
                                }
                            }
                            // Empty cells after month ends (shouldn't happen with our calculation)
                            else -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}
