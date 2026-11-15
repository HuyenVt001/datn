package com.example.snapget.feature.friends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheet
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheetData
import com.example.snapget.navigation.Screen

/**
 * Sheet ban be noi san vao [FriendsViewModel] — dung CHUNG cho Camera / Feed / Profile
 * (truoc moi man tu dung UserDetailBottomSheetData, lap code va de lech nhau).
 * Mo sheet ([visible] = true) la tu tai ban be + link moi + loi moi; bam ban -> profile,
 * icon chat -> chat 1-1, quet QR -> man QrScan (deu dong sheet truoc khi dieu huong).
 */
@Composable
fun FriendsSheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    navController: NavController,
    friendsViewModel: FriendsViewModel,
) {
    val friends by friendsViewModel.friends.collectAsState()
    val friendsStatus by friendsViewModel.friendsStatus.collectAsState()
    val inviteLink by friendsViewModel.inviteLink.collectAsState()
    val inviteLinkFailed by friendsViewModel.inviteLinkFailed.collectAsState()
    val requests by friendsViewModel.requests.collectAsState()
    val processingIds by friendsViewModel.processingRequestIds.collectAsState()
    val notice by friendsViewModel.notice.collectAsState()

    LaunchedEffect(visible) {
        if (visible) {
            friendsViewModel.loadFriends()
            friendsViewModel.loadInviteLink()
            friendsViewModel.loadRequests()
        } else {
            // Banner cu khong hien lai o lan mo sau
            friendsViewModel.clearNotice()
        }
    }

    fun closeAndGo(route: String) {
        onDismiss()
        navController.navigate(route)
    }

    UserDetailBottomSheet(
        data = if (visible) {
            UserDetailBottomSheetData(
                friends = friends,
                isLoading = friendsStatus is LoadStatus.Loading && friends.isEmpty(),
                inviteCode = inviteLink?.inviteCode,
                inviteLink = inviteLink?.link,
                inviteExpiresAt = inviteLink?.expiresAt,
                requests = requests,
                onAcceptRequest = { friendsViewModel.acceptRequest(it.id) },
                onDeclineRequest = { friendsViewModel.declineRequest(it.id) },
                onScanQrClick = { closeAndGo(Screen.QrScan.route) },
                onRemoveFriend = { friendsViewModel.removeFriend(it.id) },
                onFriendClick = { closeAndGo("profile?userId=${it.id}") },
                onMessageFriend = { closeAndGo("chat/${it.id}") },
                inviteLinkFailed = inviteLinkFailed,
                onRetryInviteLink = { friendsViewModel.loadInviteLink() },
                processingRequestIds = processingIds,
                notice = notice?.text,
                noticeIsError = notice?.isError == true,
                onNoticeShown = { friendsViewModel.clearNotice() },
            )
        } else {
            null
        },
        onDismiss = onDismiss,
    )
}
