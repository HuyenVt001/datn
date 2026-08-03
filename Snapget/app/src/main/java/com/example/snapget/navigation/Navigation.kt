package com.example.snapget.navigation

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.feature.auth.LoginScreen
import com.example.snapget.feature.camera.CameraScreen
import com.example.snapget.feature.coop.CoopCaptureScreen
import com.example.snapget.feature.friends.QrScanScreen
import com.example.snapget.feature.message.ChatScreen
import com.example.snapget.feature.message.GroupChatScreen
import com.example.snapget.feature.message.MessageScreen
import com.example.snapget.feature.post.EditMediaScreen
import com.example.snapget.feature.post.PostScreen
import com.example.snapget.feature.post.SubmitPhotoScreen
import com.example.snapget.feature.profile.UserProfile
import com.example.snapget.feature.quest.DailyQuestScreen
import com.example.snapget.feature.settings.LegalDocScreen
import com.example.snapget.feature.settings.SettingScreen
import com.example.snapget.feature.widget.HowToAddWidgetScreen
import com.example.snapget.feature.widget.WidgetSettingsScreen

sealed class Screen(val route: String) { // enum
    object Login : Screen("login")
    object Message : Screen("message")
    object Chat : Screen("chat/{recipientId}")
    object GroupChat : Screen("group_chat/{groupId}?name={name}")
    object Post : Screen("post")
    object SubmitPhoto : Screen("submit_photo")
    object Profile : Screen("profile")
    object QrScan : Screen("qr_scan")
    object Setting : Screen("setting")
    object Camera : Screen("camera")
    object DailyQuest : Screen("daily_quest")
    object EditMedia : Screen("edit_media")
    object CoopCapture : Screen("coop_capture")
    object WidgetSettings : Screen("widget_settings")
    object HowToAddWidget : Screen("how_to_add_widget")

    // Man van ban phap ly tinh: docType = "terms" | "privacy"
    object LegalDoc : Screen("legal/{docType}") {
        fun route(docType: String) = "legal/$docType"
    }
}

// https://developer.android.com/topic/architecture
// https://developer.android.com/topic/libraries/architecture/viewmodel
// https://developer.android.com/training/dependency-injection
// https://developer.android.com/develop/ui/compose/libraries#hilt
// https://github.com/android/architecture-samples

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Navigation(
    mainViewModel: MainViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState() // Define routes where bottom bar should be hidden
    val hideBottomBarRoutes = setOf(
        Screen.Message.route,
        Screen.Profile.route,
        Screen.Setting.route,
        Screen.Login.route,
        Screen.Chat.route,
        Screen.GroupChat.route,
        Screen.QrScan.route,
        Screen.DailyQuest.route,
        Screen.CoopCapture.route,
        Screen.WidgetSettings.route,
        Screen.HowToAddWidget.route,
        Screen.LegalDoc.route,
    )

    // Dang xuat (hoac mat phien) o bat ky man nao -> ve Login, xoa sach back stack.
    // NavHost KHONG tu doi startDestination sau khi da compose nen phai navigate tay.
    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated &&
            navController.currentDestination?.route != Screen.Login.route
        ) {
            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
        }
    }

    // Mo app tu widget: observe PendingRouteStore (fix 2026-07-26 — tap widget khi
    // app DANG chay cung dieu huong duoc, khong chi luc cold start / doi authState).
    // Chua dang nhap thi route nam cho, dang nhap xong se duoc consume.
    val pendingWidgetRoute by PendingRouteStore.pending.collectAsState()
    LaunchedEffect(authState, pendingWidgetRoute) {
        if (authState is AuthState.Authenticated && pendingWidgetRoute != null) {
            PendingRouteStore.consume()?.let { route ->
                val target = if (route == "camera") Screen.Camera.route else Screen.Post.route
                navController.navigate(target)
            }
        }
    }

    // Track current route as state that updates with navigation changes
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    // Check if bottom bar should be shown for current route
    val showBottomBar = currentRoute !in hideBottomBarRoutes

    // Determine start destination based on auth state
    // Da dang nhap -> vao THANG camera (BeReal-style); vuot len tren camera de xem feed
    val startDestination = when (authState) {
        is AuthState.Initial -> Screen.Login.route
        is AuthState.Authenticated -> Screen.Camera.route
        is AuthState.Unauthenticated -> Screen.Login.route
        is AuthState.Loading -> Screen.Login.route // Show login while loading
        is AuthState.Error -> Screen.Login.route
        // Da gui mail dat lai mat khau -> quay ve man Login cho user dang nhap lai
        AuthState.PasswordResetSent -> Screen.Login.route
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        // Dang nhap xong -> vao thang camera (vuot len de xem feed)
                        navController.navigate(Screen.Camera.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    // PHAI dung chung AuthViewModel scope Navigation (fix 2026-08-03):
                    // de mac dinh hiltViewModel() thi LoginScreen tao instance RIENG
                    // (scope theo back stack entry "login") -> dang nhap trong phien chi
                    // doi state instance rieng do, instance chung van Unauthenticated.
                    // Lan Sign Out sau, logout() set Unauthenticated vao gia tri Y HET
                    // (StateFlow khong emit) -> LaunchedEffect khong chay, KHONG ve man
                    // Login du Firebase da signOut -> moi API deu 401 "Thieu token".
                    viewModel = authViewModel,
                )
            }

            composable(Screen.Message.route) {
                MessageScreen(navController)
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("recipientId") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
                ChatScreen(
                    navController = navController,
                    recipientId = recipientId,
                )
            }

            // Chat nhom (<=20 thanh vien) — mo tu section "Nhom chat" man Messages
            composable(
                route = Screen.GroupChat.route,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = "Group chat"
                    },
                ),
            ) { backStackEntry ->
                GroupChatScreen(
                    navController = navController,
                    groupId = backStackEntry.arguments?.getString("groupId") ?: "",
                    groupName = backStackEntry.arguments?.getString("name") ?: "Group chat",
                )
            }

            composable(Screen.Post.route) {
                PostScreen(navController)
            }

            composable(
                route = "profile?userId={userId}",
                arguments = listOf(navArgument("userId") { nullable = true }),
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")
                UserProfile(navController = navController, userId = userId)
            }

            // Man quet QR ket ban (mo tu dialog "Add new friend" trong sheet ban be)
            composable(Screen.QrScan.route) {
                QrScanScreen(navController = navController)
            }

            composable(Screen.Setting.route) {
                // PHAI truyen authViewModel scope Navigation de logout doi dung
                // authState ma LaunchedEffect o tren dang quan sat
                SettingScreen(navController = navController, authViewModel = authViewModel)
            }

            composable(Screen.Camera.route) {
                CameraScreen(navController = navController)
            }

            // Man chinh sua sau khi chup/quay: chon khung + filter + ve tay -> Tiep -> SubmitPhoto
            composable(
                route = Screen.EditMedia.route + "?mediaPath={mediaPath}&isVideo={isVideo}",
                arguments = listOf(
                    navArgument("mediaPath") { type = NavType.StringType },
                    navArgument("isVideo") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                EditMediaScreen(
                    navController = navController,
                    mediaPath = backStackEntry.arguments?.getString("mediaPath") ?: "",
                    isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false,
                )
            }

            composable(
                route = Screen.SubmitPhoto.route +
                    "?photoPath={photoPath}&isVideo={isVideo}&frameId={frameId}&frameUrl={frameUrl}",
                arguments = listOf(
                    navArgument("photoPath") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("isVideo") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("frameId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("frameUrl") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                SubmitPhotoScreen(
                    navController = navController,
                    photoPath = backStackEntry.arguments?.getString("photoPath"),
                    isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false,
                    frameId = backStackEntry.arguments?.getString("frameId"),
                    frameUrl = backStackEntry.arguments?.getString("frameUrl"),
                )
            }

            // Man Daily Quest (2 quest co dinh/ngay + bo suu tap khung)
            composable(Screen.DailyQuest.route) {
                DailyQuestScreen(navController = navController)
            }

            // Van ban phap ly tinh (Terms / Privacy) — mo tu Settings
            composable(
                route = Screen.LegalDoc.route,
                arguments = listOf(navArgument("docType") { type = NavType.StringType }),
            ) { backStackEntry ->
                LegalDocScreen(
                    navController = navController,
                    docType = backStackEntry.arguments?.getString("docType") ?: "terms",
                )
            }

            // Widget: man cai dat (preview + toggle streak + refresh + pin)
            composable(Screen.WidgetSettings.route) {
                WidgetSettingsScreen(navController = navController)
            }

            // Widget: huong dan them widget vao man hinh chinh
            composable(Screen.HowToAddWidget.route) {
                HowToAddWidgetScreen(navController = navController)
            }

            // Chup chung (redesign 2026-08-02): man cho accept + chup nua anh + doi ghep
            composable(
                route = Screen.CoopCapture.route + "?inviteId={inviteId}&name={name}",
                arguments = listOf(
                    navArgument("inviteId") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = "friend"
                    },
                ),
            ) { backStackEntry ->
                CoopCaptureScreen(
                    navController = navController,
                    inviteId = backStackEntry.arguments?.getString("inviteId") ?: "",
                    partnerName = backStackEntry.arguments?.getString("name") ?: "friend",
                )
            }
        }
    }
}
