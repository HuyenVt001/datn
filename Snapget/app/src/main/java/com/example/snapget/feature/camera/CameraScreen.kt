package com.example.snapget.feature.camera

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.takePhotoBar
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.designsystem.preview.CameraPreviewWithZoom
import com.example.snapget.core.model.User
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.mapToUser
import com.example.snapget.navigation.Screen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraScreen(
    navController: NavController,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var selectedUser by remember {
        mutableStateOf<User?>(
            User(
                id = "everyone",
                username = "Everyone",
                avatar = "",
            ),
        )
    }
    // Use the passed onCameraClick instead of navigating to CameraXScreen

    // Get current user from auth state
    val currentUser by mainViewModel.currentUser.collectAsState()
    val data = mapToUser(currentUser)

    // Che do CHUP CHUNG (co-op): chup nua anh -> chon ban gui loi moi (thay vi dang solo)
    var coopMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MainTopBar(
                navController = navController,
                user = data, // Using user 14 as the current user
                onMessageClick = { navController.navigate(Screen.Message.route) },
                onProfileClick = {
                    {
                        data?.id?.let { userId ->
                            navController.navigate("profile?userId=$userId")
                        } ?: navController.navigate("profile")
                    }
                },
                onUserSelected = { user -> selectedUser = user },
            )
        },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Show camera view or post image based on the localCameraMode state

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent, // removes gray background
                ),
            ) {
                CameraPreviewWithZoom(
                    lifecycleOwner = lifecycleOwner,
                    height = 400.dp,
                    onPhotoTaken = { photoPath ->
                        if (coopMode) {
                            // Che do chup chung -> chon ban de gui loi moi (nua anh cua minh)
                            navController.navigate(
                                Screen.CoopSend.route + "?photoPath=" + Uri.encode(photoPath),
                            )
                        } else {
                            // Chup xong -> man chinh sua (khung + filter + ve tay) truoc khi gui
                            navController.navigate(
                                Screen.EditMedia.route + "?mediaPath=" + Uri.encode(photoPath),
                            )
                        }
                    },
                    // Giu nut chup de quay video <=5s -> cung sang man chinh sua (chi chon khung)
                    onVideoTaken = { videoPath ->
                        if (coopMode) {
                            // Server chi ghep ANH — video khong dung cho chup chung
                            Toast.makeText(
                                context,
                                "Chup chung chi ho tro anh — cham de chup nhe!",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            navController.navigate(
                                Screen.EditMedia.route + "?mediaPath=" + Uri.encode(videoPath) + "&isVideo=true",
                            )
                        }
                    },
                    showControls = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp)),
                )
            }

            // Toggle che do CHUP CHUNG (co-op): bat -> chup xong chon ban gui loi moi
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (coopMode) Color.Yellow else Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.clickable { coopMode = !coopMode },
            ) {
                Text(
                    text = if (coopMode) "👥 Chup chung: BAT" else "👥 Chup chung",
                    color = if (coopMode) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            MainBottomBar(
                navController,
                items = takePhotoBar,
            )
            // (Hang "History" + nut mui ten cu da XOA 2026-07-13 — UI chet, bam khong lam gi)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = false, backgroundColor = 0xFF000000)
@Composable
fun CameraScreenPreview() {
    CameraScreen(
        navController = rememberNavController(),
    )
}
