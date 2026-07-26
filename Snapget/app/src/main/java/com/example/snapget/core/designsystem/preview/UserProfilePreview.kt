package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.feature.profile.ProfileUi
import com.example.snapget.feature.profile.UserProfileContent

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, heightDp = 800)
@Composable
fun UserProfilePreview() {
    AppTheme {
        UserProfileContent(
            profile = ProfileUi(
                uid = "123",
                name = "John Doe",
                email = "john@example.com",
                avatar = "",
                personalStreak = 15,
                isSelf = true,
            ),
            moments = emptyList(),
            profileStatus = LoadStatus.Success(),
            updateStatus = LoadStatus.Init(),
            apiFriends = emptyList(),
            apiFriendsStatus = LoadStatus.Init(),
            inviteLink = null,
            navController = rememberNavController(),
            onLoadFriends = {},
            onRemoveFriend = {},
            onUpdateProfile = { _, _ -> },
            onResetUpdateStatus = {},
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, heightDp = 400, name = "Profile Header Only")
@Composable
fun ProfileHeaderPreview() {
    AppTheme {
        UserProfileContent(
            profile = ProfileUi(
                uid = "123",
                name = "Jane Smith",
                email = "jane@example.com",
                avatar = "",
                personalStreak = 5,
                isSelf = false,
            ),
            moments = emptyList(),
            profileStatus = LoadStatus.Success(),
            updateStatus = LoadStatus.Init(),
            apiFriends = emptyList(),
            apiFriendsStatus = LoadStatus.Init(),
            inviteLink = null,
            navController = rememberNavController(),
            onLoadFriends = {},
            onRemoveFriend = {},
            onUpdateProfile = { _, _ -> },
            onResetUpdateStatus = {},
        )
    }
}
