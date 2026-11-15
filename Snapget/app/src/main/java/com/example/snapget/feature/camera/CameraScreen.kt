package com.example.snapget.feature.camera

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.takePhotoBar
import com.example.snapget.core.designsystem.component.feedback.rememberMessenger
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheet
import com.example.snapget.core.designsystem.component.sheet.UserDetailBottomSheetData
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.designsystem.feedback.rememberFeedback
import com.example.snapget.core.designsystem.motion.pressScale
import com.example.snapget.core.designsystem.preview.CameraPreviewWithZoom
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.mapToUser
import com.example.snapget.feature.coop.CoopFriendPickerDialog
import com.example.snapget.feature.coop.CoopViewModel
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.navigation.Screen

// ⚠️ `Color.White` trong file nay la CO Y, KHONG doi sang token skin:
// chu/icon o day nam de len ANH hoac CAMERA cua nguoi dung nen phai trang
// that o MOI skin. Doi theo `SkinTheme.colors.textPrimary` thi skin nen sang
// se lam chung chim vao anh. Mau cua NEN app trong file nay van dung token.

// Vuot len qua nguong nay (tinh tu man camera) thi mo feed
private val SWIPE_UP_TO_FEED_THRESHOLD = 120.dp

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraScreen(
    navController: NavController,
    mainViewModel: MainViewModel = hiltViewModel(),
    coopViewModel: CoopViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val messenger = rememberMessenger()

    // Pill "N Friends" giua top bar -> sheet ban be (user chot 2026-11-15; thay dropdown
    // "Everyone" truoc day chon xong khong co tac dung gi)
    var showFriendSheet by remember { mutableStateOf(false) }

    // Get current user from auth state
    val currentUser by mainViewModel.currentUser.collectAsState()
    val data = mapToUser(currentUser)

    // Camera la man DAU TIEN sau login (startDestination) — phai tu tai profile
    // (ten + avatar tu Firebase/GET /users/me), khong dua vao PostScreen goi ho
    // (moi man co MainViewModel scope rieng theo nav entry)
    LaunchedEffect(Unit) {
        mainViewModel.fetchCurrentUser()
    }

    // CHUP CHUNG (redesign 2026-08-02): bam nut coop -> popup chon ban -> gui loi
    // moi (khong kem anh, TTL 5 phut) -> sang man cho/chup coop
    var showCoopPicker by remember { mutableStateOf(false) }
    val coopBusy by coopViewModel.busy.collectAsState()
    val coopError by coopViewModel.coopError.collectAsState()
    val apiFriends by friendsViewModel.friends.collectAsState()
    val friendsStatus by friendsViewModel.friendsStatus.collectAsState()
    val inviteLink by friendsViewModel.inviteLink.collectAsState()
    val friendRequests by friendsViewModel.requests.collectAsState()

    // So ban hien tren pill — tai ngay khi vao camera (truoc chi tai khi mo popup coop)
    LaunchedEffect(Unit) { friendsViewModel.loadFriends() }

    LaunchedEffect(coopError) {
        coopError?.let {
            messenger.show(it)
            coopViewModel.clearError()
        }
    }

    // Moi lan bam nut center bottom bar -> tang 1 -> CameraPreviewWithZoom chup 1 tam
    // (truoc day nut nay navigate submit_photo KHONG co anh -> "No image selected")
    var captureRequestId by remember { mutableIntStateOf(0) }

    // GIU nut center = quay video <=5s, THA = dung (nut chup trong preview da xoa —
    // hanh vi giu-de-quay chuyen sang nut center nay)
    var startRecordRequestId by remember { mutableIntStateOf(0) }
    var stopRecordRequestId by remember { mutableIntStateOf(0) }

    // Nut 🔄 "Change camera" -> doi camera truoc/sau (truoc 2026-07-26 nut nay chet)
    var flipRequestId by remember { mutableIntStateOf(0) }
    val shutterFeedback = rememberFeedback()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MainTopBar(
                navController = navController,
                user = data, // Using user 14 as the current user
                onMessageClick = { navController.navigate(Screen.Message.route) },
                // (Bug cu: lambda long lambda — bam avatar khong lam gi. Da sua 2026-07-26)
                onProfileClick = {
                    data?.id?.let { userId ->
                        navController.navigate("profile?userId=$userId")
                    } ?: navController.navigate("profile")
                },
                showProfileIcon = false,
                showMessageIcon = false,
                friendCount = apiFriends.size,
                onFriendsPillClick = {
                    friendsViewModel.loadFriends()
                    friendsViewModel.loadInviteLink()
                    friendsViewModel.loadRequests()
                    showFriendSheet = true
                },
            )
        },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // BeReal-style: vuot LEN tu man camera -> mo feed xem bai cua ban be
                .pointerInput(Unit) {
                    var dragTotal = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                        onDragEnd = {
                            if (dragTotal < -SWIPE_UP_TO_FEED_THRESHOLD.toPx()) {
                                navController.navigate(Screen.Post.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                },
        ) {
            // Show camera view or post image based on the localCameraMode state

            Card(
                // Le 16dp + shape giong khung anh man EditMedia de 2 man trong y het nhau
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = SkinTheme.shapes.image,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent, // removes gray background
                ),
            ) {
                CameraPreviewWithZoom(
                    lifecycleOwner = lifecycleOwner,
                    // Khung VUONG bo goc giong man chinh sua/bai dang (fix 2026-11-15):
                    // truoc la rong-man-hinh x 360dp -> sang EditMedia bi cat 2 ria ngang
                    aspectRatio = 1f,
                    shape = SkinTheme.shapes.image,
                    onPhotoTaken = { photoPath ->
                        // Chan double-tap nut chup (fix 2026-07-26): tam thu 2 ve khi
                        // DA roi man camera -> bo qua, khong chong 2 man EditMedia
                        if (navController.currentDestination?.route == Screen.Camera.route) {
                            // Chup xong -> man chinh sua (khung + filter + ve tay) truoc khi gui
                            navController.navigate(
                                Screen.EditMedia.route + "?mediaPath=" + Uri.encode(photoPath),
                            )
                        }
                    },
                    // Giu nut chup de quay video <=5s -> cung sang man chinh sua (chi chon khung)
                    onVideoTaken = { videoPath ->
                        if (navController.currentDestination?.route == Screen.Camera.route) {
                            navController.navigate(
                                Screen.EditMedia.route + "?mediaPath=" + Uri.encode(videoPath) + "&isVideo=true",
                            )
                        }
                    },
                    showControls = true,
                    captureRequestId = captureRequestId,
                    startRecordRequestId = startRecordRequestId,
                    stopRecordRequestId = stopRecordRequestId,
                    flipRequestId = flipRequestId,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Nut CHUP CHUNG (co-op): mo popup chon ban + gui loi moi (TTL 5 phut)
            // (2026-11-15) Surface(onClick) thay clickable ngoai Surface (ripple tran goc bo),
            // icon that thay emoji 👥, cao >= 48dp, co nhan-lun
            val coopInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    friendsViewModel.loadFriends()
                    showCoopPicker = true
                },
                shape = SkinTheme.shapes.pill,
                color = Color.Black.copy(alpha = 0.6f),
                interactionSource = coopInteraction,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .pressScale(coopInteraction),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = SkinTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Co-op",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // Nut center "Take a picture": BAM = chup anh, GIU = quay "anh GIF" <=3s,
            // THA = dung (khong navigate submit_photo rong)
            MainBottomBar(
                navController,
                windowInsets = WindowInsets(0, 0, 0, 0),
                items = takePhotoBar.map { item ->
                    when {
                        item.isCenter -> item.copy(
                            onClick = {
                                shutterFeedback.shutter() // rung + tieng chup (DESIGN.md 0.4)
                                captureRequestId++
                            },
                            onLongPress = {
                                shutterFeedback.shutter()
                                startRecordRequestId++
                            },
                            // Tha tay o MOI lan bam — preview tu bo qua neu khong dang quay
                            onPressRelease = { stopRecordRequestId++ },
                        )
                        // Nut 🔄: lat camera truoc/sau
                        item.title == "Change camera" -> item.copy(onClick = { flipRequestId++ })
                        else -> item
                    }
                },
            )
            // (Hang "History" + nut mui ten cu da XOA 2026-07-13 — UI chet, bam khong lam gi)
        }
    }

    // Sheet quan ly ban be (dung chung voi Feed/Profile): dem ban, QR ket ban, loi moi
    UserDetailBottomSheet(
        data = if (showFriendSheet) {
            UserDetailBottomSheetData(
                friends = apiFriends,
                isLoading = friendsStatus is LoadStatus.Loading,
                inviteCode = inviteLink?.inviteCode,
                inviteLink = inviteLink?.link,
                inviteExpiresAt = inviteLink?.expiresAt,
                requests = friendRequests,
                onAcceptRequest = { request -> friendsViewModel.acceptRequest(request.id) },
                onDeclineRequest = { request -> friendsViewModel.declineRequest(request.id) },
                onScanQrClick = {
                    showFriendSheet = false
                    navController.navigate(Screen.QrScan.route)
                },
                onRemoveFriend = { friend -> friendsViewModel.removeFriend(friend.id) },
            )
        } else {
            null
        },
        onDismiss = { showFriendSheet = false },
    )

    // Popup chon ban gui loi moi chup chung -> thanh cong sang man cho/chup coop
    if (showCoopPicker) {
        CoopFriendPickerDialog(
            friends = apiFriends,
            busy = coopBusy,
            loading = friendsStatus is LoadStatus.Loading,
            onSend = { friend ->
                coopViewModel.createInvite(friend.id) { invite ->
                    showCoopPicker = false
                    navController.navigate(
                        Screen.CoopCapture.route +
                            "?inviteId=" + invite.inviteId +
                            "&name=" + Uri.encode(friend.name),
                    )
                }
            },
            onDismiss = { showCoopPicker = false },
        )
    }
}
