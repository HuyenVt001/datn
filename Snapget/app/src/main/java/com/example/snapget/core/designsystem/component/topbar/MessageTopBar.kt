package com.example.snapget.core.designsystem.component.topbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.component.common.CommonTopBar

@Composable
fun ListMessageTopBar(navController: NavController) {
    CommonTopBar(
        navController = navController,
        title = "Messages",
    )
}

@Preview(showBackground = true)
@Composable
fun ListMessageTopBarPreview() {
    ListMessageTopBar(navController = rememberNavController())
}
