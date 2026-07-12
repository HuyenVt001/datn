package com.example.snapget

import android.Manifest
import android.app.ComponentCaller
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.snapget.core.config.statusBarConfig
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.edgeToEdgeWithStyle
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.navigation.Navigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted: $granted")
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Android 13+ needs runtime permission to show FCM notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // replace with using in AndroidManifest.xml
        // WindowCompat.setDecorFitsSystemWindows(window, false)

        statusBarConfig(window)

        // Apply edge-to-edge style after super.onCreate to ensure it takes effect
        edgeToEdgeWithStyle()

        setContent { SnapgetApp() }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent)

        // Lấy uri từ deep link
        val data: Uri? = intent?.data
        if (data != null) {
            when (data.host) {
                "auth" -> {
                    when (data.lastPathSegment) {
                        "success" -> {
                            // TODO: xử lý login thành công
                        }
                        "failure" -> {
                            // TODO: xử lý login thất bại
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SnapgetApp(
    mainViewModel: MainViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()

    // Debug current auth state and user details
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                Log.d("UserDebug", "Auth State: Authenticated")
                mainViewModel.fetchCurrentUser()
            }

            else -> Log.d("UserDebug", "Auth State: ${authState::class.simpleName}")
        }
    }

    // Only log when user details change
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            Log.d("UserDebug", "User Details: data=$user")
            // Fetch posts for this specific user
            mainViewModel.getPostsOfUser(user.id)
            // Fetch posts from user and friends for the main feed
            mainViewModel.getAllPostsOfUserAndFriends(user)
        }
    }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Navigation(mainViewModel, authViewModel)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, showSystemUi = true)
@Composable
@RestrictTo(RestrictTo.Scope.TESTS)
fun SnapgetAppPreview() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Navigation()
        }
    }
}
