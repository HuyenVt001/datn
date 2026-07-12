package com.example.snapget.core.designsystem.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.component.list.ExternalAppComponent
import com.example.snapget.core.designsystem.component.list.ShareYourLinkComponent
import com.example.snapget.core.designsystem.component.list.TotalFriendComponent
import com.example.snapget.core.designsystem.component.list.YourFriendAppComponent
import com.example.snapget.core.designsystem.theme.GrayError
import com.example.snapget.core.designsystem.theme.GraySurface
import com.example.snapget.core.model.FriendUi

/**
 * Data + callback cua sheet ban be. inviteCode/inviteLink de sinh QR moi ket ban;
 * onScanQrClick mo man quet QR (navigation do man hinh cha quyet dinh).
 */
data class UserDetailBottomSheetData(
    val friends: List<FriendUi> = emptyList(),
    val isLoading: Boolean = false,
    val inviteCode: String? = null,
    val inviteLink: String? = null,
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

            ExternalAppComponent()

            YourFriendAppComponent(
                friends = sheetData.friends,
                onRemoveFriend = { friendPendingRemove = it },
                isLoading = sheetData.isLoading,
            )

            ShareYourLinkComponent()
        }

        if (showQrDialog) {
            AddFriendQrDialog(
                inviteCode = sheetData.inviteCode,
                inviteLink = sheetData.inviteLink,
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
                        text = "Xóa bạn bè?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "Xóa ${friend.name} khỏi danh sách bạn bè? " +
                            "Streak chung sẽ mất và phải quét QR để kết bạn lại.",
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
                        Text(text = "Xóa", color = GrayError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendPendingRemove = null }) {
                        Text(text = "Hủy", color = Color.White)
                    }
                },
            )
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
                inviteLink = "https://snapget.app/invite/ABC123",
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
