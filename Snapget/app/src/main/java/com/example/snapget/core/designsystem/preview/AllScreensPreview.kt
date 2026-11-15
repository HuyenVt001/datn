package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.bottombar.MainBottomBar
import com.example.snapget.core.designsystem.component.bottombar.takePhotoBar
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.component.pill.MessageInputPill
import com.example.snapget.core.designsystem.component.pill.UserListWithArrows
import com.example.snapget.core.designsystem.component.topbar.MainTopBar
import com.example.snapget.core.designsystem.component.topbar.SettingScreenTopBar
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.ShopItemDto
import com.example.snapget.core.network.dto.TodayQuestDto
import com.example.snapget.feature.appearance.AppearanceScreenContent
import com.example.snapget.feature.appearance.AppearanceUiState
import com.example.snapget.feature.auth.AuthMode
import com.example.snapget.feature.auth.AuthUIActions
import com.example.snapget.feature.auth.AuthUIState
import com.example.snapget.feature.auth.LoginScreenContent
import com.example.snapget.feature.message.ConversationItem
import com.example.snapget.feature.profile.ProfileUi
import com.example.snapget.feature.profile.UserProfileContent
import com.example.snapget.feature.quest.DailyQuestScreenContent
import com.example.snapget.feature.quest.QuestUiState
import com.example.snapget.feature.settings.LegalDocScreen
import com.example.snapget.feature.settings.SettingScreenContent
import com.example.snapget.feature.shop.ShopScreenContent
import com.example.snapget.feature.shop.ShopTab
import com.example.snapget.feature.shop.ShopUiState
import com.example.snapget.feature.widget.HowToAddWidgetScreen
import com.example.snapget.feature.widget.WidgetSettingsScreenContent
import com.example.snapget.feature.widget.data.WidgetSnapshot
import com.example.snapget.feature.widget.data.WidgetStateKind

/**
 * Danh sach tat ca cac man hinh trong Snapget de chon preview qua PreviewParameter.
 */
class ScreenTypeProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Home Feed",
        "Camera",
        "Messages List",
        "Direct Chat",
        "User Profile",
        "Daily Quests",
        "Shop",
        "Appearance & Themes",
        "Settings",
        "Widget Settings",
        "How To Add Widget",
        "QR Code Scanner",
        "Legal Terms",
        "Login",
        "Register",
    )
}

/**
 * Preview tong hop cho phep chuyen doi qua dropdown giua tat ca cac man hinh trong Android Studio.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Preview(
    name = "All Screens (Selectable)",
    showBackground = true,
    showSystemUi = true,
)
@Composable
fun AllScreensPreview(
    @PreviewParameter(ScreenTypeProvider::class) screenType: String,
) {
    AppTheme {
        when (screenType) {
            "Home Feed" -> HomeFeedMock()
            "Camera" -> CameraScreenMock()
            "Messages List" -> MessageListMock()
            "Direct Chat" -> ChatScreenMock()
            "User Profile" -> UserProfileMock()
            "Daily Quests" -> DailyQuestMock()
            "Shop" -> ShopMock()
            "Appearance & Themes" -> AppearanceMock()
            "Settings" -> SettingsMock()
            "Widget Settings" -> WidgetSettingsMock()
            "How To Add Widget" -> HowToAddWidgetMock()
            "QR Code Scanner" -> QrScanMock()
            "Legal Terms" -> LegalDocMock()
            "Login" -> LoginMock(AuthMode.LOGIN)
            "Register" -> LoginMock(AuthMode.REGISTER)
            else -> HomeFeedMock()
        }
    }
}

// =============================================================================
// CAC PREVIEW RIENG LE CHO TUNG SCREEN (HIEN THI DANG GALLERY TRONG ANDROID STUDIO)
// =============================================================================

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "1. Home Feed", showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeFeedScreen() {
    AppTheme { HomeFeedMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "2. Camera Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewCameraScreen() {
    AppTheme { CameraScreenMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "3. Messages List", showBackground = true, showSystemUi = true)
@Composable
fun PreviewMessagesScreen() {
    AppTheme { MessageListMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "4. Direct Chat (1-on-1)", showBackground = true, showSystemUi = true)
@Composable
fun PreviewChatScreen() {
    AppTheme { ChatScreenMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "5. User Profile", showBackground = true, showSystemUi = true)
@Composable
fun PreviewUserProfileScreen() {
    AppTheme { UserProfileMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "6. Daily Quests", showBackground = true, showSystemUi = true)
@Composable
fun PreviewDailyQuestScreen() {
    AppTheme { DailyQuestMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "7. Shop Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewShopScreen() {
    AppTheme { ShopMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "8. Appearance & Themes", showBackground = true, showSystemUi = true)
@Composable
fun PreviewAppearanceScreen() {
    AppTheme { AppearanceMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "9. Settings Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewSettingsScreen() {
    AppTheme { SettingsMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "10. Widget Settings", showBackground = true, showSystemUi = true)
@Composable
fun PreviewWidgetSettingsScreen() {
    AppTheme { WidgetSettingsMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "11. How To Add Widget", showBackground = true, showSystemUi = true)
@Composable
fun PreviewHowToAddWidgetScreen() {
    AppTheme { HowToAddWidgetMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "12. QR Code Scanner", showBackground = true, showSystemUi = true)
@Composable
fun PreviewQrScanScreen() {
    AppTheme { QrScanMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "13. Legal Terms & Privacy", showBackground = true, showSystemUi = true)
@Composable
fun PreviewLegalDocScreen() {
    AppTheme { LegalDocMock() }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "14. Login Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewLoginScreen() {
    AppTheme { LoginMock(AuthMode.LOGIN) }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "15. Register Screen", showBackground = true, showSystemUi = true)
@Composable
fun PreviewRegisterScreen() {
    AppTheme { LoginMock(AuthMode.REGISTER) }
}

// =============================================================================
// MOCK IMPLEMENTATIONS (STATELESS + SAMPLE DATA — KHONG CALL HILTVIEWMODEL)
// =============================================================================

/** 1. Home / Posts Feed Mock */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun HomeFeedMock() {
    Scaffold(
        topBar = {
            SettingScreenTopBar(rememberNavController())
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            UserListWithArrows(users = SampleData.users.take(5), showEveryone = true)
            PostGrid(
                posts = SampleData.samplePosts,
                onPostClick = {},
                modifier = Modifier.weight(1f),
            )
            MessageInputPill()
        }
    }
}

/** 2. Camera Viewfinder Mock */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CameraScreenMock() {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            MainTopBar(
                navController = navController,
                user = SampleData.users.firstOrNull(),
                onMessageClick = {},
                onProfileClick = {},
                onUserSelected = {},
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Simulated camera viewfinder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp),
                )
            }

            Surface(
                shape = SkinTheme.shapes.sheet,
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Text(
                    text = "👥 Co-op",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            MainBottomBar(
                navController = navController,
                items = takePhotoBar,
            )
        }
    }
}

/** 3. Messages List Mock */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MessageListMock() {
    Scaffold(
        topBar = {
            SimpleTopBar(title = "Messages", onBackClick = {})
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            for (conversation in sampleConversations) {
                ConversationItem(conversation = conversation)
            }
        }
    }
}

/** 4. 1-on-1 Chat Mock */
@Composable
private fun ChatScreenMock() {
    Scaffold(
        topBar = {
            SimpleTopBar(title = "Jane Smith", onBackClick = {})
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Received message
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "Hey! Did you check out the new quests today? 🎯",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                // Sent message
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                        color = SkinTheme.colors.accent,
                    ) {
                        Text(
                            text = "Yeah, just completed the camera challenge! 📸",
                            modifier = Modifier.padding(12.dp),
                            color = SkinTheme.colors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                // Received message
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "Nice! Let's do a Co-op capture later 😊",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            MessageInputPill()
        }
    }
}

/** 5. User Profile Mock */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun UserProfileMock() {
    UserProfileContent(
        profile = ProfileUi(
            uid = "123",
            name = "John Doe",
            email = "john@example.com",
            avatar = "",
            personalStreak = 15,
            isSelf = true,
        ),
        moments = emptyList(),
        profileStatus = LoadStatus.Success(),
        updateStatus = LoadStatus.Init(),
        apiFriends = emptyList(),
        apiFriendsStatus = LoadStatus.Init(),
        inviteLink = null,
        navController = rememberNavController(),
        onLoadFriends = {},
        onRemoveFriend = {},
        onUpdateProfile = { _, _ -> },
        onResetUpdateStatus = {},
    )
}

/** 6. Daily Quests Mock */
@Composable
private fun DailyQuestMock() {
    DailyQuestScreenContent(
        uiState = QuestUiState(
            status = LoadStatus.Success(),
            quests = listOf(
                TodayQuestDto(
                    questId = "1",
                    type = "LOGIN",
                    content = "Login to Snapget",
                    releaseDate = "2026-10-23",
                    completed = true,
                ),
                TodayQuestDto(
                    questId = "2",
                    type = "POST_MOMENT",
                    content = "Post any photo or GIF",
                    releaseDate = "2026-10-23",
                    completed = false,
                ),
                TodayQuestDto(
                    questId = "3",
                    type = "AI_CHALLENGE",
                    content = "Take a photo of a cup up close on the table",
                    releaseDate = "2026-10-23",
                    targetClass = "cup",
                    completed = false,
                ),
            ),
            rewardAstrite = 60,
            personalStreak = 7,
        ),
    )
}

/** 7. Shop Screen Mock (thay Gacha 2026-11-15) */
@Composable
private fun ShopMock() {
    ShopScreenContent(
        uiState = ShopUiState(
            status = LoadStatus.Success(),
            astrite = 1600,
            tab = ShopTab.SKINS,
            items = listOf(
                ShopItemDto(itemId = "s1", itemName = "Snow", itemType = "SKIN", refId = "1", price = 4800),
                ShopItemDto(
                    itemId = "s2",
                    itemName = "Forest",
                    itemType = "SKIN",
                    refId = "2",
                    price = 4800,
                    isOwned = true,
                ),
            ),
        ),
    )
}

/** 8. Appearance & Themes Mock */
@Composable
private fun AppearanceMock() {
    AppearanceScreenContent(
        uiState = AppearanceUiState(
            status = LoadStatus.Success(),
            frames = listOf(
                FrameDto(frameId = "f1", frameName = "Gold Frame", isUnlocked = true),
                FrameDto(frameId = "f2", frameName = "Neon Cyber", isUnlocked = false),
                FrameDto(frameId = "f3", frameName = "Starry Night", isUnlocked = true),
            ),
            currentSkinId = 0,
            currentEffectId = 0,
        ),
        selectedTab = 0,
    )
}

/** 9. Settings Mock */
@Composable
private fun SettingsMock() {
    SettingScreenContent(
        settings = SampleData.settingList,
        navController = rememberNavController(),
    )
}

/** 10. Widget Settings Mock */
@Composable
private fun WidgetSettingsMock() {
    WidgetSettingsScreenContent(
        streakOnWidget = true,
        snapshot = WidgetSnapshot(
            streak = 15,
            kind = WidgetStateKind.OK,
        ),
        navController = rememberNavController(),
    )
}

/** 11. How To Add Widget Mock */
@Composable
private fun HowToAddWidgetMock() {
    HowToAddWidgetScreen(
        navController = rememberNavController(),
    )
}

/** 12. QR Code Scanner Mock */
@Composable
private fun QrScanMock() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .border(3.dp, SkinTheme.colors.accent, SkinTheme.shapes.sheet),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = SkinTheme.shapes.pill,
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Text(
                    text = "Point at your friend's QR code",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 13. Legal Documents Mock */
@Composable
private fun LegalDocMock() {
    LegalDocScreen(
        navController = rememberNavController(),
        docType = "terms",
    )
}

/** 14 & 15. Auth / Login / Register Mock */
@Composable
private fun LoginMock(mode: AuthMode = AuthMode.LOGIN) {
    LoginScreenContent(
        uiState = AuthUIState(
            authState = AuthState.Initial,
            isLoading = false,
            authMode = mode,
            email = "user@example.com",
            password = "",
            passwordVisible = false,
        ),
        actions = object : AuthUIActions {
            override fun onLogin(email: String, password: String) {}
            override fun onRegister(email: String, password: String, name: String) {}
            override fun onResetPassword(email: String) {}
            override fun onGoogleLogin(activity: ComponentActivity) {}
            override fun onLoginSuccess() {}
            override fun clearError() {}
        },
    )
}
