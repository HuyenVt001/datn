package com.example.snapget.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.snapget.core.constants.MAX_GROUP_SIZE
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.network.dto.ChatGroupDetailDto
import com.example.snapget.core.network.dto.GroupMemberDto
import com.example.snapget.core.util.avatarOrDefault

/**
 * Sheet cai dat nhom chat — mo tu nut ⋯ tren header GroupChatScreen.
 * Bo cuc theo anh mau Messenger (user chot 2026-08-02): avatar nhom (bam de doi)
 * + ten nhom (but chi sua) + hang Invite + danh sach thanh vien (⋯ xoa — chi
 * NGUOI TAO nhom thay) + card Mute notifications / Leave group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsSheet(
    detail: ChatGroupDetailDto,
    myUid: String?,
    friends: List<FriendUi>,
    busy: Boolean,
    onRename: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onInvite: (List<String>) -> Unit,
    onRemoveMember: (GroupMemberDto) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    // uid thanh vien dang mo menu ⋯ (DropdownMenu anchor theo hang)
    var memberMenuFor by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<GroupMemberDto?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    val isCreator = myUid != null && myUid == detail.createdBy
    val muted = myUid != null && myUid in detail.mutedBy

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ==== Avatar nhom (bam de doi anh; spinner de len khi dang upload) ====
            Box(contentAlignment = Alignment.Center) {
                GroupAvatarCircle(
                    avatar = detail.avatar,
                    size = 96.dp,
                    iconSize = 44.dp,
                    onClick = { if (!busy) onPickAvatar() },
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(96.dp),
                        color = Color.Yellow,
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==== Ten nhom + but chi doi ten ====
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showRenameDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = detail.groupName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Change group name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==== Hang Invite (vong tron net dut + dau cong, kieu Messenger) ====
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showInviteDialog = true }
                    .padding(vertical = 8.dp),
            ) {
                val dashColor = MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            drawCircle(
                                color = dashColor,
                                radius = size.minDimension / 2 - 1.dp.toPx(),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Invite",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Invite",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "New members can see previous messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ==== Danh sach thanh vien (<=20 — Column thuong, khong can lazy) ====
            detail.members.forEach { member ->
                val isSelf = member.uid == myUid
                val memberName = member.fullName ?: "Snapget user"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    GroupMemberAvatar(avatar = member.avatar, name = memberName)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSelf) "$memberName (You)" else memberName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (member.uid == detail.createdBy) {
                            Text(
                                text = "Group creator",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Menu ⋯ chi hien cho NGUOI TAO nhom va khong tro vao chinh minh
                    // (server cung chi cho creator xoa — day la chan UX)
                    if (isCreator && !isSelf) {
                        Box {
                            IconButton(onClick = { memberMenuFor = member.uid }) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "Member options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = memberMenuFor == member.uid,
                                onDismissRequest = { memberMenuFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Remove from group",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        memberMenuFor = null
                                        removeTarget = member
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==== Card Mute notifications + Leave group ====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2C2C2C)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Mute notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // Toggle trung tinh trang/xam theo anh mau (accent vang chi cho capture)
                    Switch(
                        checked = muted,
                        onCheckedChange = { if (!busy) onMuteChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6E6E6E),
                            uncheckedThumbColor = Color(0xFFB0B0B0),
                            uncheckedTrackColor = Color(0xFF3A3A3A),
                        ),
                    )
                }
                HorizontalDivider(color = Color(0xFF3A3A3A))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLeaveConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Leave group",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = detail.groupName,
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showInviteDialog) {
        InviteMembersDialog(
            candidates = friends.filter { it.id !in detail.memberIds },
            currentSize = detail.memberIds.size,
            onConfirm = { memberIds ->
                onInvite(memberIds)
                showInviteDialog = false
            },
            onDismiss = { showInviteDialog = false },
        )
    }

    removeTarget?.let { member ->
        val memberName = member.fullName ?: "Snapget user"
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF2C2C2C),
            title = {
                Text(text = "Remove member?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "$memberName will be removed from \"${detail.groupName}\".",
                    color = Color(0xFFB0B0B0),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(member)
                        removeTarget = null
                    },
                ) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(text = "Cancel", color = Color.White)
                }
            },
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = Color(0xFF2C2C2C),
            title = {
                Text(text = "Leave group?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "You will no longer see messages from \"${detail.groupName}\".",
                    color = Color(0xFFB0B0B0),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        onLeave()
                    },
                ) {
                    Text(
                        text = "Leave",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(text = "Cancel", color = Color.White)
                }
            },
        )
    }
}

/** Avatar nhom: co anh -> hien anh; chua co -> icon Groups vang tren nen #404137. */
@Composable
private fun GroupAvatarCircle(
    avatar: String?,
    size: Dp,
    iconSize: Dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF404137))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = "Group photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = "Group photo",
                tint = Color.Yellow,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Avatar 1 thanh vien (44dp) — fallback DiceBear theo ten nhu cac man khac. */
@Composable
private fun GroupMemberAvatar(avatar: String?, name: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF404137)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = avatarOrDefault(avatar, name),
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** Dialog doi ten nhom (style khop CreateGroupDialog). */
@Composable
private fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2C),
        title = {
            Text(text = "Change group name", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != currentName,
            ) {
                Text(text = "Save", color = Color.Yellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.White)
            }
        },
    )
}

/**
 * Dialog moi them ban vao nhom: tick chon ban be CHUA o trong nhom,
 * chan chon qua gioi han 20 thanh vien (server enforce that).
 */
@Composable
private fun InviteMembersDialog(
    candidates: List<FriendUi>,
    currentSize: Int,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateListOf<String>() }
    val slotsLeft = MAX_GROUP_SIZE - currentSize

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2C),
        title = {
            Text(text = "Invite friends", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "${currentSize + selected.size}/$MAX_GROUP_SIZE members",
                    color = Color(0xFFB0B0B0),
                    style = MaterialTheme.typography.labelMedium,
                )
                when {
                    candidates.isEmpty() -> Text(
                        text = "All your friends are already in this group.",
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    slotsLeft <= 0 -> Text(
                        text = "This group is full ($MAX_GROUP_SIZE members).",
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(candidates) { friend ->
                        val checked = selected.contains(friend.id)
                        val canSelectMore = selected.size < slotsLeft
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = checked || canSelectMore) {
                                    if (checked) selected.remove(friend.id) else selected.add(friend.id)
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = checked || canSelectMore,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selected.add(friend.id) else selected.remove(friend.id)
                                },
                            )
                            Text(
                                text = friend.name,
                                color = Color.White,
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
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(text = "Invite", color = Color.Yellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.White)
            }
        },
    )
}
