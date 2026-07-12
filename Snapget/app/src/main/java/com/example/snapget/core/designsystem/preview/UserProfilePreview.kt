package com.example.snapget.core.designsystem.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.feature.profile.UserProfile

@Preview(showBackground = true, heightDp = 800)
@Composable
fun UserProfilePreview() {
    AppTheme {
        UserProfile(navController = rememberNavController())
    }
}

@Preview(showBackground = true, heightDp = 400, name = "Profile Header Only")
@Composable
fun ProfileHeaderPreview() {
    AppTheme {
        UserProfile(navController = rememberNavController())
    }
}
