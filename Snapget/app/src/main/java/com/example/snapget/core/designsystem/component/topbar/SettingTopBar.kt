package com.example.snapget.core.designsystem.component.topbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import com.example.snapget.navigation.Screen

@Composable
fun SettingScreenTopBar(navController: NavController) {
    CommonTopBar(
        navController = navController,
        title = "Settings",
        startIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onStartIconClick = { navController.popBackStack() },
        endIcon = Icons.AutoMirrored.Filled.ArrowForward,
        onEndIconClick = { navController.navigate(Screen.Profile.route) },
    )
}

@Preview(showBackground = true)
@Composable
fun ListSettingTopBarPreview() {
    SettingScreenTopBar(navController = rememberNavController())
}
