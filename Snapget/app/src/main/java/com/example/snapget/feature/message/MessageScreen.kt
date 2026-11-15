package com.example.snapget.feature.message

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.ImageSetting
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import com.example.snapget.core.designsystem.component.feedback.rememberMessenger
import com.example.snapget.core.designsystem.component.state.EmptyState
import com.example.snapget.core.designsystem.component.state.ErrorState
import com.example.snapget.core.designsystem.component.state.SkeletonList
import com.example.snapget.core.designsystem.motion.popOnChange
import com.example.snapget.core.designsystem.motion.pressScale
import com.example.snapget.core.designsystem.motion.staggerEnter
import com.example.snapget.core.designsystem.skin.SkinIcon
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.network.dto.ChatGroupDto
import com.example.snapget.core.util.avatarOrDefault
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Danh sach hoi thoai 1-1 — doc tu API /messages/conversations (da migrate
 * khoi Firestore 2026-07-12). Avatar VIEN VANG khi co tin chua doc (DESIGN 7.8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MessageScreen(
    navController: NavHostController,
    messageViewModel: MessageViewModel = hiltViewModel(),
) {
    val conversations by messageViewModel.conversations.collectAsState()
    val status by messageViewModel.conversationsStatus.collectAsState()
    val groups by messageViewModel.groups.collectAsState()
    val friendsById by messageViewModel.friendsById.collectAsState()
    val sendError by messageViewModel.sendError.collectAsState()
    val context = LocalContext.current
    val messenger = rememberMessenger()

    // Dialog tao nhom chat (chon ban + dat ten)
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    // Refresh moi lan mo man (khong realtime — REST thuan); dung chung Retry + keo-de-refresh
    val reload: () -> Unit = {
        messageViewModel.loadConversations()
        messageViewModel.loadGroups()
    }
    LaunchedEffect(Unit) { reload() }

    // Loi tao nhom / gui tin -> Toast message cua server
    LaunchedEffect(sendError) {
        sendError?.let {
            messenger.show(it)
            messageViewModel.clearSendError()
        }
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = "Messages",
                // Theo theme: hardcode trang la vo hinh o Light mode
                titleColor = MaterialTheme.colorScheme.onBackground,
                // Nut tao nhom chat (<=20 thanh vien)
                endIcon = Icons.Default.GroupAdd,
                onEndIconClick = { showCreateGroupDialog = true },
            )
        },
    ) { paddingValues ->
        if (showCreateGroupDialog) {
            CreateGroupDialog(
                friends = friendsById.values.toList(),
                onCreate = { name, memberIds ->
                    messageViewModel.createGroup(name, memberIds)
                    showCreateGroupDialog = false
                },
                onDismiss = { showCreateGroupDialog = false },
            )
        }

        when {
            status is LoadStatus.Loading && conversations.isEmpty() && groups.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    SkeletonList() // P5: skeleton hang hoi thoai thay spinner
                }
            }

            // Loi tai -> Error + Retry (2026-11-15: truoc hien duoi chu "No messages yet")
            conversations.isEmpty() && groups.isEmpty() && status is LoadStatus.Error -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    ErrorState(message = (status as LoadStatus.Error).error, onRetry = reload)
                }
            }

            conversations.isEmpty() && groups.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "No chats yet",
                        message = "Add friends from the Camera screen, then say hi here 👋",
                    )
                }
            }

            // Keo xuong de tai lai (2026-11-15)
            else -> PullToRefreshBox(
                isRefreshing = status is LoadStatus.Loading,
                onRefresh = reload,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                // Nhom chat truoc, roi den hoi thoai 1-1
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (groups.isNotEmpty()) {
                        item {
                            Text(
                                text = "Group chats",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(groups) { group ->
                            GroupItem(
                                group = group,
                                onClick = {
                                    navController.navigate(
                                        "group_chat/${group.groupId}?name=" +
                                            Uri.encode(group.groupName),
                                    )
                                },
                            )
                        }
                        item {
                            Text(
                                text = "Messages",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    itemsIndexed(conversations, key = { _, c -> c.counterpartId }) { index, conversation ->
                        ConversationItem(
                            conversation = conversation,
                            modifier = Modifier.staggerEnter(index),
                            onClick = {
                                navController.navigate("chat/${conversation.counterpartId}")
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/** 1 dong nhom chat: avatar nhom (icon nhom neu chua dat) + ten + so thanh vien. */
@Composable
private fun GroupItem(
    group: ChatGroupDto,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(SkinTheme.colors.pill),
            contentAlignment = Alignment.Center,
        ) {
            if (!group.avatar.isNullOrBlank()) {
                // Avatar nhom da dat (doi trong sheet cai dat nhom)
                AsyncImage(
                    model = group.avatar,
                    contentDescription = "Group",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Group",
                    tint = SkinTheme.colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${group.memberIds.size} members",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SkinIcon(
            res = SkinTheme.icons.chevronRight,
            fallback = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Open group",
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * Dialog tao nhom chat: dat ten + tick chon ban be (<=20 thanh vien
 * — server enforce, nguoi tao tu vao nhom).
 */
@Composable
private fun CreateGroupDialog(
    friends: List<FriendUi>,
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkinTheme.colors.surfaceVariant,
        title = {
            Text(text = "Create group chat", color = SkinTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(100) },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pick members (${selected.size})",
                    color = SkinTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (friends.isEmpty()) {
                    Text(
                        text = "No friends yet — add a friend first!",
                        color = SkinTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(friends) { friend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selected.contains(friend.id)) {
                                        selected.remove(friend.id)
                                    } else {
                                        selected.add(friend.id)
                                    }
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = selected.contains(friend.id),
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(friend.id) else selected.remove(friend.id)
                                },
                            )
                            Text(
                                text = friend.name,
                                color = SkinTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(groupName.trim(), selected.toList()) },
                enabled = groupName.isNotBlank() && selected.isNotEmpty(),
            ) {
                Text(text = "Create", color = SkinTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = SkinTheme.colors.textPrimary)
            }
        },
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationItem(
    conversation: ConversationUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth().pressScale(interaction, pressedScale = 0.98f),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = SkinTheme.shapes.input,
        onClick = onClick,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar — vien VANG khi co tin chua doc (DESIGN 7.8)
            Circle(
                imageSetting = ImageSetting(
                    imageUrl = avatarOrDefault(conversation.avatar, conversation.name),
                    contentDescription = "Profile picture",
                ),
                gap = if (conversation.unread) 3.dp else 0.dp,
                outerSize = 50.dp,
                backgroundColor = SkinTheme.colors.pill,
                borderColor = if (conversation.unread) SkinTheme.colors.accent else SkinTheme.colors.pill,
                onClick = onClick,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Message content
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Sender name and time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(Modifier.weight(1f))

                    // Ban moi ket chua co tin nhan (sendTime rong) -> khong hien gio
                    // (formatTime se fallback "Now" gay hieu nham la co tin moi)
                    if (conversation.sendTime.isNotBlank()) {
                        Text(
                            text = formatTime(conversation.sendTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (conversation.unread) SkinTheme.colors.badge else SkinTheme.colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message preview
                Text(
                    text = conversation.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation.unread) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Chua doc = cham vang (badge) thay chevron; da doc = chevron chim.
            // Chevron trang tri -> contentDescription null (Card da co onClick).
            if (conversation.unread) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .popOnChange(conversation.unread)
                        .background(SkinTheme.colors.badge, CircleShape)
                        .semantics { contentDescription = "Unread" },
                )
            } else {
                SkinIcon(
                    res = SkinTheme.icons.chevronRight,
                    fallback = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = SkinTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Parse thoi gian server (ISO UTC dang ...Z) ve gio may; fallback
 * LocalDateTime thuan cho du lieu cu.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal fun parseServerTime(iso: String): LocalDateTime? = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime()
} catch (_: Exception) {
    try {
        LocalDateTime.parse(iso)
    } catch (_: Exception) {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun formatTime(timeString: String): String {
    val time = parseServerTime(timeString) ?: return "Now"
    val now = LocalDateTime.now()

    return when {
        time.toLocalDate() == now.toLocalDate() -> {
            time.format(DateTimeFormatter.ofPattern("HH:mm"))
        }

        time.toLocalDate() == now.toLocalDate().minusDays(1) -> {
            "Yesterday"
        }

        time.year == now.year -> {
            time.format(DateTimeFormatter.ofPattern("MMM d"))
        }

        else -> {
            time.format(DateTimeFormatter.ofPattern("yyyy MMM d"))
        }
    }
}
