package com.example.snapget.core.designsystem.component.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.navigation.Screen

val buttons = listOf(
    Triple(Icons.Filled.Group, "", "FRIEND"),
    Triple(Icons.Filled.Settings, Screen.Setting.route, "Settings"),
    Triple(Icons.AutoMirrored.Filled.KeyboardArrowRight, Screen.Post.route, "Home"),
)

// Nut "Get Locket Gold" da XOA (2026-07-13): toan bo tinh nang mien phi, khong co thanh toan
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileTopBar(
    navController: NavController,
    onFriendsClick: () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = Modifier.padding(horizontal = 16.dp),
        title = {},
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                buttons.forEach { (icon, route, description) ->
                    Button(
                        onClick = {
                            if (description == "FRIEND") {
                                onFriendsClick()
                            } else {
                                navController.navigate(route)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = description,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun UserProfileTopBarPreview() {
    UserProfileTopBar(
        navController = rememberNavController(),
        onFriendsClick = {},
    )
}
