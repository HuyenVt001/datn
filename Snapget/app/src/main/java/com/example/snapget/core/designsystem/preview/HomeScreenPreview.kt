package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.component.pill.MessageInputPill
import com.example.snapget.core.designsystem.component.pill.UserListWithArrows
import com.example.snapget.core.designsystem.component.topbar.SettingScreenTopBar
import com.example.snapget.feature.post.PostScreen

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun HomeScreenSamplePreview() {
    PostScreen(rememberNavController())
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    Scaffold(
        topBar = {
            SettingScreenTopBar(rememberNavController())
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            UserListWithArrows(users = SampleData.users.take(3), showEveryone = true)
            PostGrid(
                modifier = Modifier.padding(paddingValues),
            )
            MessageInputPill()
//            MainBottomBar()
        }
    }
}
