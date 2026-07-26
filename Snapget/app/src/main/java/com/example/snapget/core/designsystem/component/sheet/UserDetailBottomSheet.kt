package com.example.snapget.core.designsystem.component.sheet

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.snapget.core.designsystem.component.list.ExternalAppComponent
import com.example.snapget.core.designsystem.component.list.ShareYourLinkComponent
import com.example.snapget.core.designsystem.component.list.TotalFriendComponent
import com.example.snapget.core.designsystem.component.list.YourFriendAppComponent
import com.example.snapget.core.designsystem.theme.GrayError
import com.example.snapget.core.designsystem.theme.GrayOnSurfaceVariant
import com.example.snapget.core.designsystem.theme.GraySurface
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.util.avatarOrDefault

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
)

/**
 * Sheet quan ly ban be (asset: user_detail_bottom_sheet.png): "X out of 20 friends",
 * pill "Add new friend" (mo dialog QR), danh sach ban (streak + xoa co xac nhan),
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
            // Simple drag handle without excessive padding
            HorizontalDivider(
                color = Color.White,
                thickness = 3.dp,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .background(Color(0xFF121212))
                    .width(40.dp),
            )
        },
        containerColor = Color(0xFF121212),
    ) { sheetData ->
        // Dialog QR (pill "Add new friend") + dialog xac nhan xoa ban
        var showQrDialog by remember { mutableStateOf(false) }
        var friendPendingRemove by remember { mutableStateOf<FriendUi?>(null) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f) // Limit height to 90% of the screen
                .background(Color(0xFF121212))
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

            // Loi moi ket ban dang cho MINH xac nhan (thiet ke 2 buoc — chu link accept/decline)
            if (sheetData.requests.isNotEmpty()) {
                FriendRequestsSection(
                    requests = sheetData.requests,
                    onAccept = sheetData.onAcceptRequest,
                    onDecline = sheetData.onDeclineRequest,
                )
            }

            ExternalAppComponent()

            YourFriendAppComponent(
                friends = sheetData.friends,
                onRemoveFriend = { friendPendingRemove = it },
                isLoading = sheetData.isLoading,
            )

            ShareYourLinkComponent(inviteLink = sheetData.inviteLink)
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

        // Xoa ban la hanh dong mat mat (streak chung ve 0) -> luon hoi xac nhan
        friendPendingRemove?.let { friend ->
            AlertDialog(
                onDismissRequest = { friendPendingRemove = null },
                containerColor = GraySurface,
                title = {
                    Text(
                        text = "Remove friend?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "Remove ${friend.name} from your friends? " +
                            "Your shared streak will be lost and you'll need to scan QR to reconnect.",
                        color = Color.White.copy(alpha = 0.8f),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            sheetData.onRemoveFriend(friend)
                            friendPendingRemove = null
                        },
                    ) {
                        Text(text = "Remove", color = GrayError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendPendingRemove = null }) {
                        Text(text = "Cancel", color = Color.White)
                    }
                },
            )
        }
    }
}

/**
 * Section "Lời mời kết bạn": moi loi moi 1 hang avatar + ten + nut ✓ chap nhan
 * (trang) / ✕ tu choi (do GrayError). Chi hien khi co loi moi dang cho.
 */
@Composable
private fun FriendRequestsSection(
    requests: List<FriendUi>,
    onAccept: (FriendUi) -> Unit,
    onDecline: (FriendUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "💌 Friend requests",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GraySurface,
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
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "wants to be your friend",
                                color = GrayOnSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        // ✓ chap nhan — nut tron trang (hanh dong chinh)
                        Surface(shape = CircleShape, color = Color.White) {
                            IconButton(
                                onClick = { onAccept(request) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Accept ${request.name}",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // ✕ tu choi — vien do, xoa im lang
                        IconButton(
                            onClick = { onDecline(request) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Decline ${request.name}",
                                tint = GrayError,
                            )
                        }
                    }
                }
            }
        }
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
                    FriendUi(id = "1", name = "An Nguyen", streak = 5),
                    FriendUi(id = "2", name = "Binh Tran", streak = 0),
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
