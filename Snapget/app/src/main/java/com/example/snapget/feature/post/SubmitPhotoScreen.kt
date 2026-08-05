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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.snapget.core.designsystem.component.input.InputCaptionPill
import com.example.snapget.core.designsystem.component.list.FriendList
import com.example.snapget.core.designsystem.component.sheet.CaptionBottomSheet
import com.example.snapget.core.designsystem.component.sheet.CaptionBottomSheetData
import com.example.snapget.core.designsystem.component.sheet.generalCaptions
import com.example.snapget.core.designsystem.component.sheet.rememberCurrentTime
import com.example.snapget.core.designsystem.component.video.GifVideoPlayer
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.User
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.mapToUser
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.navigation.Screen
import java.io.File
import kotlinx.coroutines.launch

// ⚠️ `Color.White` trong file nay la CO Y, KHONG doi sang token skin:
// chu/icon o day nam de len ANH hoac CAMERA cua nguoi dung nen phai trang
// that o MOI skin. Doi theo `SkinTheme.colors.textPrimary` thi skin nen sang
// se lam chung chim vao anh. Mau cua NEN app trong file nay van dung token.

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
    // Danh sach ban that tu API /friendships — de chon nguoi gui kem vao chat
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val submitStatus by postViewModel.submitStatus.collectAsState()
    val chatSendError by postViewModel.chatSendError.collectAsState()
    val apiFriends by friendsViewModel.friends.collectAsState()

    // Tai ban be 1 lan khi mo man (de list chon nguoi gui kem co data that)
    LaunchedEffect(Unit) { friendsViewModel.loadFriends() }

    // Loi gui kem vao chat (bai dang VAN thanh cong) -> toast rieng
    LaunchedEffect(chatSendError) {
        chatSendError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            postViewModel.clearChatSendError()
        }
    }

    // FriendUi (API) -> User cho FriendList
    val friendUsers = remember(apiFriends) {
        apiFriends.map { User(id = it.id, username = it.name, email = "", avatar = it.avatar) }
    }

    val frameImageUrl = frameUrl

    // Xu ly ket qua dang bai: thanh cong -> ve feed; loi -> bao message tu server
    LaunchedEffect(submitStatus) {
        when (val status = submitStatus) {
            is LoadStatus.Success -> {
                Toast.makeText(context, "Posted successfully!", Toast.LENGTH_SHORT).show()
                postViewModel.resetSubmitStatus()
                // Ve feed xem bai vua dang; don EditMedia/SubmitPhoto khoi stack,
                // giu Camera lam man goc (back tu feed -> camera)
                navController.navigate(Screen.Post.route) {
                    popUpTo(Screen.Camera.route)
                }
            }
            is LoadStatus.Error -> {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                postViewModel.resetSubmitStatus()
            }
            else -> Unit
        }
    }

    // null = KHONG gui kem cho ai (mac dinh — chi dang len feed);
    // chon 1 ban = gui kem vao chat voi ban do; chon "everyone" = gui cho TAT CA ban be
    var selectedUser by remember { mutableStateOf<User?>(null) }

    // Add bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var showCaptionSheet by remember { mutableStateOf(false) }

    // Caption cua bai dang: go truc tiep vao o tren anh HOAC chon chip tu
    // Captions List (chip dien noi dung vao o, sua tiep duoc); gui kem khi Send
    var caption by remember { mutableStateOf<String?>(null) }

    // Id chong dang TRUNG: sinh 1 lan cho MOI lan vao man nay, giu nguyen qua cac
    // lan bam dang lai (request truoc timeout nhung bai DA len -> server tra bai cu)
    val submitRequestId = remember { java.util.UUID.randomUUID().toString() }

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
                // Mac dinh la DANG THANG len feed; gui cho ban cu the chi la tuy chon
                // (nut Download cu da XOA 2026-07-19 — UI chet, bam chi ghi log)
                title = "New Post",
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
                        // "Anh GIF" <=3s: xem truoc dung nhu luc dang — tu chay lap, khong tieng
                        GifVideoPlayer(
                            source = photoPath,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(SkinTheme.shapes.image),
                        )
                    } else {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = "Captured photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(SkinTheme.shapes.image),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    // Khung da chon o man Edit — overlay phu kin preview
                    if (frameImageUrl != null) {
                        AsyncImage(
                            model = frameImageUrl,
                            contentDescription = "Photo frame",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(SkinTheme.shapes.image),
                        )
                    }

                    // O caption LUON hien tren anh de go truc tiep (2026-08-02 —
                    // truoc day chi hien sau khi chon chip tu Captions List).
                    // Pill trang mo chu den, placeholder "Add a caption..." (DESIGN.md 7.7)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    ) {
                        InputCaptionPill(
                            text = caption.orEmpty(),
                            onTextChange = { caption = it },
                        )
                    }

                    // Overlay loading khi dang upload + tao moment
                    if (submitStatus is LoadStatus.Loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = SkinTheme.shapes.image,
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

            // (PageIndicator 5 cham cu da XOA 2026-07-19 — trang tri gia, khong co trang nao)
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
                                    "No photo yet — take one first.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                submitStatus is LoadStatus.Loading -> Unit // dang gui, bo qua
                                else -> postViewModel.submitPhoto(
                                    File(photoPath),
                                    isVideo = isVideo,
                                    caption = caption?.trim()?.takeIf { it.isNotEmpty() },
                                    frameId = frameId,
                                    // Khong chon ai (mac dinh) = chi dang len feed;
                                    // chon 1 ban = gui kem chat 1-1; Everyone = gui MOI ban be
                                    sendToUids = when (selectedUser?.id) {
                                        null -> emptyList()
                                        "everyone" -> friendUsers.map { it.id }
                                        else -> listOf(selectedUser!!.id)
                                    },
                                    clientRequestId = submitRequestId,
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
                    // Khong hien pill "You" (khong gui cho chinh minh);
                    // Everyone nam CUOI danh sach (GenericCircleList)
                    FriendList(
                        user = null,
                        friends = friendUsers,
                        selectedFriendId = selectedUser?.id ?: "",
                        // Bam lan 2 vao muc dang chon = BO chon (ve mac dinh: chi dang)
                        onFriendSelected = { user ->
                            selectedUser = if (selectedUser?.id == user.id) null else user
                        },
                    )
                }
            }

            // Giai thich hanh vi theo lua chon hien tai
            Text(
                text = when (selectedUser?.id) {
                    null -> "Posts to the feed — pick a friend or Everyone to also send it in chat (optional)"
                    "everyone" -> "Posts to the feed + sends to ALL friends in chat"
                    else -> "Posts to the feed + sends to ${selectedUser?.username ?: "a friend"} in chat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
