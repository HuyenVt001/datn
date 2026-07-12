package com.example.snapget.navigation

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.example.snapget.core.designsystem.preview.PlaceholderScreen
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.feature.auth.LoginScreen
import com.example.snapget.feature.camera.CameraScreen
import com.example.snapget.feature.friends.QrScanScreen
import com.example.snapget.feature.message.ChatScreen
import com.example.snapget.feature.message.MessageScreen
import com.example.snapget.feature.post.PostScreen
import com.example.snapget.feature.post.SubmitPhotoScreen
import com.example.snapget.feature.profile.UserProfile
import com.example.snapget.feature.settings.SettingScreen

sealed class Screen(val route: String) { // enum
    object Login : Screen("login")
    object Message : Screen("message")
    object Chat : Screen("chat/{recipientId}")
    object Post : Screen("post")
    object SubmitPhoto : Screen("submit_photo")
    object PostDetail : Screen("post_detail/{postId}")
    object Profile : Screen("profile")
    object QrScan : Screen("qr_scan")
    object Setting : Screen("setting")
    object Detail : Screen("detail")
    object Camera : Screen("camera")
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
        Screen.QrScan.route,
    )

    // Track current route as state that updates with navigation changes
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    // Check if bottom bar should be shown for current route
    val showBottomBar = currentRoute !in hideBottomBarRoutes

    // Determine start destination based on auth state
    val startDestination = when (authState) {
        is AuthState.Initial -> Screen.Login.route
        is AuthState.Authenticated -> Screen.Post.route
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
                        navController.navigate(Screen.Post.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
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
                SettingScreen(navController, mainViewModel, authViewModel)
            }

            composable(Screen.Camera.route) {
                CameraScreen(navController = navController)
            }

            composable(
                route = Screen.SubmitPhoto.route + "?photoPath={photoPath}",
                arguments = listOf(
                    navArgument("photoPath") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                SubmitPhotoScreen(
                    navController = navController,
                    photoPath = backStackEntry.arguments?.getString("photoPath"),
                )
            }

            composable(Screen.Detail.route) {
                PlaceholderScreen(title = "Detail", navController = navController)
            }
        }
    }
}
