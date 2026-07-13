package com.example.snapget

import android.Manifest
import android.app.ComponentCaller
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import com.example.snapget.core.config.statusBarConfig
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.network.serverMessage
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.edgeToEdgeWithStyle
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.feature.friends.data.FriendsRepository
import com.example.snapget.navigation.Navigation
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted: $granted")
        }

    /** Data layer ket ban — dung cho deep link moi ket ban (khong qua ViewModel). */
    @Inject
    lateinit var friendsRepository: FriendsRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // App mo bang deep link khi CHUA chay (cold start) -> intent nam o onCreate
        handleInviteDeepLink(intent)

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

        // App dang chay, bam link moi ket ban -> singleTask nen intent vao day.
        // (Nhanh deep link `auth/success|failure` cu da XOA — manifest khong dang ky
        // intent-filter nao cho host `auth`; quen mat khau di qua email cua Firebase.)
        handleInviteDeepLink(intent)
    }

    /**
     * Deep link moi ket ban: https://snapget.app/invite/{code}.
     * Da dang nhap -> goi POST /friendships/connect ngay va bao ket qua bang Toast
     * (loi nghiep vu nhu du 20 ban / ma sai hien message tieng Viet cua server).
     */
    private fun handleInviteDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.host != "snapget.app" || !data.toString().contains("/invite/")) return
        val code = FriendsRepository.parseInviteCode(data.toString())
        if (code.isEmpty()) return

        if (firebaseAuth.currentUser == null) {
            Toast.makeText(this, "Dang nhap roi bam lai link de ket ban nhe!", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                friendsRepository.connect(code)
                Toast.makeText(this@MainActivity, "Ket ban thanh cong! 🎉", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.serverMessage("Ket ban that bai."), Toast.LENGTH_LONG).show()
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
    // (Cac fetch Firestore cu getPostsOfUser/getAllPostsOfUserAndFriends da XOA
    //  2026-07-13 — feed doc tu API server trong PostScreen/PostViewModel)
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            Log.d("UserDebug", "User Details: data=$user")
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
