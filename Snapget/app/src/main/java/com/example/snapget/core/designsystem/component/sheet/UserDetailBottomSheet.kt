package com.example.snapget.core.designsystem.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.snapget.core.constants.MAX_FRIENDS
import com.example.snapget.core.designsystem.component.list.ExternalAppComponent
import com.example.snapget.core.designsystem.component.list.ShareYourLinkComponent
import com.example.snapget.core.designsystem.component.list.TotalFriendComponent
import com.example.snapget.core.designsystem.component.list.YourFriendAppComponent
import com.example.snapget.core.designsystem.skin.SkinSheetDragHandle
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.timeAgo
import kotlinx.coroutines.delay

/**
 * Data + callback cua sheet ban be. inviteCode/inviteLink de sinh QR moi ket ban
 * (inviteExpiresAt = han 30 ngay cua link); requests = loi moi ket ban dang cho
 * MINH (chu link) accept/decline; onScanQrClick mo man quet QR
 * (navigation do man hinh cha quyet dinh).
 */
data class UserDetailBottomSheetData(
    val friends: List<FriendUi> = emptyList(),
    val isLoading: Boolean = false,
    val inviteCode: String? = null,
    val inviteLink: String? = null,
    val inviteExpiresAt: String? = null,
    val requests: List<FriendUi> = emptyList(),
    val onAcceptRequest: (FriendUi) -> Unit = {},
    val onDeclineRequest: (FriendUi) -> Unit = {},
    val onScanQrClick: () -> Unit = {},
    val onRemoveFriend: (FriendUi) -> Unit = {},
    // Bam hang ban -> mo profile; icon chat -> mo chat 1-1 (navigation do man cha quyet dinh)
    val onFriendClick: (FriendUi) -> Unit = {},
    val onMessageFriend: (FriendUi) -> Unit = {},
    // Tai link moi that bai (chua co link) -> the link hien nut Retry
    val inviteLinkFailed: Boolean = false,
    val onRetryInviteLink: () -> Unit = {},
    // uid loi moi dang xu ly -> khoa nut ✓/✕ + spinner
    val processingRequestIds: Set<String> = emptySet(),
    // Ket qua chap nhan/tu choi/xoa (banner trong sheet — Snackbar app bi sheet che)
    val notice: String? = null,
    val noticeIsError: Boolean = false,
    val onNoticeShown: () -> Unit = {},
)

/**
 * Sheet quan ly ban be (asset: user_detail_bottom_sheet.png): "X out of 20 friends",
 * pill "Add new friend" (mo dialog QR), danh sach ban (xoa co xac nhan),
 * cac section share. Ket ban CHI qua ma moi QR — khop server /friendships.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailBottomSheet(
    data: UserDetailBottomSheetData?,
    onDismiss: () -> Unit,
) {
    AnimatedBottomSheet(
        value = data,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            // Simple drag handle without excessive padding (+ dai ornament cua skin)
            SkinSheetDragHandle(sheetColor = SkinTheme.colors.background) {
                HorizontalDivider(
                    color = SkinTheme.colors.textPrimary,
                    thickness = 3.dp,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(40.dp),
                )
            }
        },
        containerColor = SkinTheme.colors.background,
    ) { sheetData ->
        // Dialog QR (pill "Add new friend") + dialog xac nhan xoa ban
        var showQrDialog by remember { mutableStateOf(false) }
        var friendPendingRemove by remember { mutableStateOf<FriendUi?>(null) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f) // Limit height to 90% of the screen
                .background(SkinTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp), // Add bottom padding for scroll end
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TotalFriendComponent(
                totalFriends = sheetData.friends.size,
                onAddFriendClick = { showQrDialog = true },
                modifier = Modifier.padding(top = 0.dp),
            )

            // Banner ket qua thao tac (tu an sau vai giay)
            sheetData.notice?.let { text ->
                NoticeBanner(
                    text = text,
                    isError = sheetData.noticeIsError,
                    onShown = sheetData.onNoticeShown,
                )
            }

            // Loi moi ket ban dang cho MINH xac nhan (thiet ke 2 buoc — chu link accept/decline)
            if (sheetData.requests.isNotEmpty()) {
                FriendRequestsSection(
                    requests = sheetData.requests,
                    processingIds = sheetData.processingRequestIds,
                    isFull = sheetData.friends.size >= MAX_FRIENDS,
                    onAccept = sheetData.onAcceptRequest,
                    onDecline = sheetData.onDeclineRequest,
                )
            }

            ExternalAppComponent(inviteLink = sheetData.inviteLink)

            YourFriendAppComponent(
                friends = sheetData.friends,
                onRemoveFriend = { friendPendingRemove = it },
                onFriendClick = sheetData.onFriendClick,
                onMessageClick = sheetData.onMessageFriend,
                isLoading = sheetData.isLoading,
            )

            ShareYourLinkComponent(
                inviteLink = sheetData.inviteLink,
                inviteExpiresAt = sheetData.inviteExpiresAt,
                isLinkFailed = sheetData.inviteLinkFailed,
                onRetryLink = sheetData.onRetryInviteLink,
                onShowQr = { showQrDialog = true },
            )
        }

        if (showQrDialog) {
            AddFriendQrDialog(
                inviteCode = sheetData.inviteCode,
                inviteLink = sheetData.inviteLink,
                expiresAt = sheetData.inviteExpiresAt,
                onScanClick = {
                    showQrDialog = false
                    sheetData.onScanQrClick()
                },
                onDismiss = { showQrDialog = false },
            )
        }

        // Xoa ban la hanh dong kho hoan tac -> luon hoi xac nhan
        friendPendingRemove?.let { friend ->
            AlertDialog(
                onDismissRequest = { friendPendingRemove = null },
                containerColor = SkinTheme.colors.surface,
                title = {
                    Text(
                        text = "Remove friend?",
                        color = SkinTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "Remove ${friend.name} from your friends? " +
                            "You'll need to scan their QR code again to reconnect.",
                        color = SkinTheme.colors.textPrimary.copy(alpha = 0.8f),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            sheetData.onRemoveFriend(friend)
                            friendPendingRemove = null
                        },
                    ) {
                        Text(text = "Remove", color = SkinTheme.colors.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendPendingRemove = null }) {
                        Text(text = "Cancel", color = SkinTheme.colors.textPrimary)
                    }
                },
            )
        }
    }
}

/**
 * Section "Lời mời kết bạn": moi loi moi 1 hang avatar + ten + nut ✓ chap nhan
 * (trang) / ✕ tu choi (do SkinTheme.colors.error). Chi hien khi co loi moi dang cho.
 */
@Composable
private fun FriendRequestsSection(
    requests: List<FriendUi>,
    processingIds: Set<String>,
    isFull: Boolean,
    onAccept: (FriendUi) -> Unit,
    onDecline: (FriendUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "💌 Friend requests (${requests.size})",
            color = SkinTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Du 20 ban thi server se tu choi accept -> bao truoc thay vi de user bam roi moi loi
        if (isFull) {
            Text(
                text = "Your friend list is full. Remove a friend before accepting new requests.",
                color = SkinTheme.colors.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Surface(
            shape = SkinTheme.shapes.input,
            color = SkinTheme.colors.surface,
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                requests.forEach { request ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = avatarOrDefault(request.avatar, request.name),
                            contentDescription = "Avatar ${request.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = request.name,
                                color = SkinTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "wants to be your friend",
                                color = SkinTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            // Thoi diem gui loi moi ("2h ago"); khong parse duoc -> an
                            timeAgo(request.requestedAt)?.let { ago ->
                                Text(
                                    text = ago,
                                    color = SkinTheme.colors.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (request.id in processingIds) {
                            // Dang goi API -> spinner thay 2 nut (chan bam lap)
                            CircularProgressIndicator(
                                color = SkinTheme.colors.textPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(horizontal = 26.dp)
                                    .size(24.dp),
                            )
                        } else {
                            // ✓ chap nhan — nut tron trang (hanh dong chinh); mo khi du 20 ban
                            Surface(
                                shape = CircleShape,
                                color = SkinTheme.colors.textPrimary.copy(alpha = if (isFull) 0.35f else 1f),
                            ) {
                                IconButton(
                                    onClick = { onAccept(request) },
                                    enabled = !isFull,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Accept ${request.name}",
                                        tint = Color.Black,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // ✕ tu choi — xoa im lang (nguoi gui khong duoc bao)
                            IconButton(
                                onClick = { onDecline(request) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Decline ${request.name}",
                                    tint = SkinTheme.colors.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Banner ket qua trong sheet: tu goi [onShown] (an) sau 3.5s; bam de an ngay. */
@Composable
private fun NoticeBanner(
    text: String,
    isError: Boolean,
    onShown: () -> Unit,
) {
    LaunchedEffect(text) {
        delay(3_500)
        onShown()
    }
    Surface(
        shape = SkinTheme.shapes.input,
        color = if (isError) SkinTheme.colors.error else SkinTheme.colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onShown() },
    ) {
        Text(
            text = text,
            color = if (isError) Color.White else Color.Black,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreen() {
    var sheetData by remember { mutableStateOf<UserDetailBottomSheetData?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            sheetData = UserDetailBottomSheetData(
                friends = listOf(
                    FriendUi(id = "1", name = "An Nguyen"),
                    FriendUi(id = "2", name = "Binh Tran"),
                ),
                isLoading = false,
                inviteCode = "ABC123",
                inviteLink = "https://snapget-d8693.web.app/invite/ABC123",
                inviteExpiresAt = "2026-08-18T00:00:00.000Z",
            )
        }) {
            Text("Show User Detail Bottom Sheet")
        }
    }

    UserDetailBottomSheet(
        data = sheetData,
        onDismiss = { sheetData = null },
    )
}
