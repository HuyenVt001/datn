package com.example.snapget.core.designsystem.component.topbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.component.common.CommonTopBar

@Composable
fun SettingScreenTopBar(navController: NavController) {
    // Nut mui ten -> Profile ben PHAI DA XOA (fix 2026-07-27): trong nhu 2 nut back
    // 2 ben, gay nham lan — man Settings chi can 1 nut back ben trai
    CommonTopBar(
        navController = navController,
        title = "Settings",
        startIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onStartIconClick = { navController.popBackStack() },
    )
}

@Preview(showBackground = true)
@Composable
fun ListSettingTopBarPreview() {
    SettingScreenTopBar(navController = rememberNavController())
}
