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
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.config.statusBarConfig
import com.example.snapget.core.designsystem.component.sheet.InviteConfirmDialog
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.ui.MainViewModel
import com.example.snapget.core.util.edgeToEdgeWithStyle
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.feature.friends.data.FriendsRepository
import com.example.snapget.feature.friends.data.PendingInviteStore
import com.example.snapget.feature.widget.SnapgetWidget
import com.example.snapget.navigation.Navigation
import com.example.snapget.navigation.PendingRouteStore
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted: $granted")
        }

    /**
     * VM ket ban scope theo Activity — CUNG instance voi hiltViewModel() o goc
     * SnapgetApp, de deep link (xu ly o day) mo duoc dialog xac nhan trong Compose.
     */
    private val friendsViewModel: FriendsViewModel by viewModels()

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // App mo bang deep link khi CHUA chay (cold start) -> intent nam o onCreate
        handleInviteDeepLink(intent)
        handleWidgetRoute(intent)

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
        handleWidgetRoute(intent)
    }

    /** Tap widget -> luu route cho Navigation dieu huong sau khi da Authenticated. */
    private fun handleWidgetRoute(intent: Intent?) {
        intent?.getStringExtra(SnapgetWidget.EXTRA_WIDGET_ROUTE)?.let { route ->
            PendingRouteStore.set(route)
        }
    }

    /**
     * Deep link moi ket ban: https://snapget-d8693.web.app/invite/{code} (App Links)
     * hoac snapget://invite/{code} (scheme du phong tu landing page hosting).
     * Da dang nhap -> mo dialog XAC NHAN (ten + avatar nguoi moi) qua FriendsViewModel;
     * chua dang nhap -> luu ma (PendingInviteStore), login xong tu mo dialog.
     */
    private fun handleInviteDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val isInviteLink = (data.scheme == "snapget" && data.host == "invite") ||
            (data.host == "snapget-d8693.web.app" && data.path?.startsWith("/invite/") == true)
        if (!isInviteLink) return
        val code = FriendsRepository.parseInviteCode(data.toString())
        if (code.isEmpty()) return

        if (firebaseAuth.currentUser == null) {
            PendingInviteStore.save(this, code)
            Toast.makeText(this, "Invite saved — log in to connect!", Toast.LENGTH_LONG).show()
            return
        }
        friendsViewModel.startInviteConfirm(code)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SnapgetApp(
    mainViewModel: MainViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    // Cung instance voi MainActivity.friendsViewModel (deu scope theo Activity)
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()
    val themeMode by mainViewModel.themeMode.collectAsState()
    val inviteConfirm by friendsViewModel.inviteConfirm.collectAsState()
    val inviteConnectStatus by friendsViewModel.connectStatus.collectAsState()
    val context = LocalContext.current

    // Debug current auth state and user details
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                Log.d("UserDebug", "Auth State: Authenticated")
                mainViewModel.fetchCurrentUser()
                // Bam link moi luc CHUA dang nhap -> ma da duoc luu; gio login xong
                // thi tu mo dialog xac nhan, khong bat bam lai link
                PendingInviteStore.consume(context)?.let { code ->
                    friendsViewModel.startInviteConfirm(code)
                }
            }

            else -> Log.d("UserDebug", "Auth State: ${authState::class.simpleName}")
        }
    }

    // Bao ket qua gui loi moi tu deep link (dialog xac nhan cap Activity — man QR co observer rieng)
    LaunchedEffect(inviteConnectStatus) {
        when (val status = inviteConnectStatus) {
            is LoadStatus.Success -> {
                // "Da gui loi moi — cho X xac nhan" (PENDING) / "Ket ban thanh cong" (mutual)
                Toast.makeText(context, friendsViewModel.lastConnectMessage, Toast.LENGTH_LONG).show()
                friendsViewModel.resetConnectStatus()
            }

            is LoadStatus.Error -> {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                friendsViewModel.resetConnectStatus()
            }

            else -> Unit
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

    AppTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Navigation(mainViewModel, authViewModel)
        }

        // Dialog xac nhan ket ban tu deep link — de len tren moi man hinh
        inviteConfirm?.let { confirm ->
            InviteConfirmDialog(
                info = confirm.info,
                error = confirm.error,
                onConfirm = { friendsViewModel.confirmInvite() },
                onDismiss = { friendsViewModel.dismissInviteConfirm() },
            )
        }
    }
}

// LUU Y: da bo SnapgetAppPreview (2026-07-16) — render ca Navigation() keo theo
// moi screen + hiltViewModel nen preview luon fail "Failed to instantiate a ViewModel".
