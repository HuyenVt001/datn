package com.example.snapget.feature.post

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.submitPhotoBar
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import com.example.snapget.core.designsystem.component.indicator.PageIndicator
import com.example.snapget.core.designsystem.component.input.InputCaptionPill
import com.example.snapget.core.designsystem.component.list.FriendList
import com.example.snapget.core.designsystem.component.sheet.CaptionBottomSheet
import com.example.snapget.core.designsystem.component.sheet.CaptionBottomSheetData
import com.example.snapget.core.designsystem.component.sheet.generalCaptions
import com.example.snapget.core.designsystem.component.sheet.rememberCurrentTime
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.User
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.mapToUser
import com.example.snapget.navigation.Screen
import java.io.File
import kotlinx.coroutines.launch

val submitButtonSize = 80.dp

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SubmitPhotoScreen(
    navController: NavController,
    photoPath: String? = null,
    // true = file la video (<=5s) — tu man EditMedia chuyen sang
    isVideo: Boolean = false,
    // Khung da chon o man EditMedia (gui kem moment, hien overlay khi xem)
    frameId: String? = null,
    // URL anh khung — EditMedia truyen thang qua route, khong tai lai catalog /frames
    frameUrl: String? = null,
    mainViewModel: MainViewModel = hiltViewModel(),
    postViewModel: PostViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val submitStatus by postViewModel.submitStatus.collectAsState()

    val frameImageUrl = frameUrl

    // Xu ly ket qua dang bai: thanh cong -> ve feed; loi -> bao message tu server
    LaunchedEffect(submitStatus) {
        when (val status = submitStatus) {
            is LoadStatus.Success -> {
                Toast.makeText(context, "Dang bai thanh cong!", Toast.LENGTH_SHORT).show()
                postViewModel.resetSubmitStatus()
                navController.navigate(Screen.Post.route) {
                    popUpTo(Screen.Post.route) { inclusive = true }
                }
            }
            is LoadStatus.Error -> {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                postViewModel.resetSubmitStatus()
            }
            else -> Unit
        }
    }

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

    // Add bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var showCaptionSheet by remember { mutableStateOf(false) }

    // Caption cua bai dang: null = chua co pill; "" = pill rong dang go; gui kem khi Send
    var caption by remember { mutableStateOf<String?>(null) }

    // Get current time to use as remember key
    val currentTime = rememberCurrentTime()

    // Get captions with current time dependency
    val captions = generalCaptions()

    // Optimize by using remember with time dependency - only recreates when time changes
    val captionBottomSheetData = remember(currentTime) {
        CaptionBottomSheetData(
            items = captions,
        )
    }

    // Get current user from auth state
    val currentUser by mainViewModel.currentUser.collectAsState()
    val data = mapToUser(currentUser)

    val maxCaptionLength = 30

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = "Send to...",
                endIcon = Icons.Outlined.Download,
                onEndIconClick = {
                    Log.d("SubmitPhotoScreen", "Download icon clicked")
                },
            )
        },
    ) { paddingValues ->
        // Add CaptionBottomSheet when needed
        if (showCaptionSheet) {
            CaptionBottomSheet(
                data = captionBottomSheetData,
                onDismiss = {
                    showCaptionSheet = false
                    coroutineScope.launch { sheetState.hide() }
                },
                onCaptionSelected = { item ->
                    // Chip "Text" -> pill rong de tu go; chip khac -> dien noi dung chip, sua tiep duoc
                    caption = if (item.title == "Text") "" else item.title
                    showCaptionSheet = false
                    coroutineScope.launch { sheetState.hide() }
                },
            )
        }

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Show camera view or post image based on the localCameraMode state

            Log.d("SubmitPhotoScreen", "Current user: ${data.username}, ID: ${data.id}")

            // Anh VUA CHUP tu camera (luong dang bai that) — uu tien hien thi
            if (photoPath != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    if (isVideo) {
                        // Video <=5s: placeholder toi + icon play (video phat that o feed)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = "Anh vua chup",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    // Khung da chon o man Edit — overlay phu kin preview
                    if (frameImageUrl != null) {
                        AsyncImage(
                            model = frameImageUrl,
                            contentDescription = "Khung anh",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp)),
                        )
                    }

                    // Caption dang nhap — pill trang mo chu den (DESIGN.md 7.7)
                    caption?.let { current ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                        ) {
                            InputCaptionPill(
                                text = current,
                                onTextChange = { caption = it },
                            )
                        }
                    }

                    // Overlay loading khi dang upload + tao moment
                    if (submitStatus is LoadStatus.Loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(20.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            } else {
                // Placeholder for camera view or empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.LightGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No image selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.DarkGray,
                    )
                }
            }

            PageIndicator(
                totalPages = 5,
                currentPage = 2,
                modifier = Modifier.size(100.dp),
                inactiveColor = Color.Gray.copy(alpha = 0.3f),
            )

            MainBottomBar(
                navController,
                items = submitPhotoBar,
                onItemClick = { item ->
                    when (item.title) {
                        "Captions List" -> {
                            showCaptionSheet = true
                            coroutineScope.launch { sheetState.show() }
                        }
                        // Nut Send: upload anh -> tao moment qua server
                        "Send" -> {
                            when {
                                photoPath == null -> Toast.makeText(
                                    context,
                                    "Chua co anh — hay chup truoc da.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                submitStatus is LoadStatus.Loading -> Unit // dang gui, bo qua
                                else -> postViewModel.submitPhoto(
                                    File(photoPath),
                                    isVideo = isVideo,
                                    caption = caption?.trim()?.takeIf { it.isNotEmpty() },
                                    frameId = frameId,
                                )
                            }
                        }
                    }
                },
            )

            // Friend list centered with submit button
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                var containerWidth by remember { mutableIntStateOf(0) }
                var itemWidth by remember { mutableIntStateOf(0) }
                val scrollState = rememberScrollState()
                val density = LocalDensity.current

                LaunchedEffect(containerWidth, itemWidth) {
                    if (containerWidth > 0 && itemWidth > 0) {
                        val scrollOffset = (containerWidth / 2f - itemWidth / 2f).toInt()
                        scrollState.scrollTo(scrollOffset)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            containerWidth = coordinates.size.width
                        }
                        .horizontalScroll(scrollState),
                ) {
                    FriendList(
                        user = data,
                        selectedFriendId = selectedUser?.id ?: "everyone",
                        onFriendSelected = { selectedUser = it },
                    )
                }
            }
        }
    }
}
