package com.example.snapget.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.data.SettingIds
import com.example.snapget.core.designsystem.component.feedback.rememberMessenger
import com.example.snapget.core.designsystem.component.topbar.SettingScreenTopBar
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.skin.glassColor
import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.SettingType
import com.example.snapget.feature.auth.AuthViewModel
import com.example.snapget.navigation.Screen

// URL mang xa hoi cua app (placeholder theo handle @snapgetapp trong description)
private const val TIKTOK_URL = "https://www.tiktok.com/@snapgetapp"
private const val INSTAGRAM_URL = "https://www.instagram.com/snapgetapp"
private const val TWITTER_URL = "https://x.com/snapgetapp"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val messenger = rememberMessenger()
    val settings by settingsViewModel.settings.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val saveStatus by settingsViewModel.saveStatus.collectAsState()
    val inviteLink by settingsViewModel.inviteLink.collectAsState()

    var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
    var showBirthdayPicker by rememberSaveable { mutableStateOf(false) }

    // Ket qua luu Edit Name / Birthday: thanh cong -> toast + dong dialog; loi -> toast, giu dialog
    LaunchedEffect(saveStatus) {
        when (val status = saveStatus) {
            is LoadStatus.Success -> {
                messenger.show("Saved")
                showEditNameDialog = false
                showBirthdayPicker = false
                settingsViewModel.resetSaveStatus()
            }

            is LoadStatus.Error -> {
                messenger.show(status.error)
                settingsViewModel.resetSaveStatus()
            }

            else -> Unit
        }
    }

    SettingScreenContent(
        settings = settings,
        navController = navController,
        onToggleChanged = { settingId, isToggled ->
            settingsViewModel.updateToggle(settingId, isToggled)
        },
        onSettingClick = { setting ->
            // Dispatch theo id ON DINH (SettingIds) — khong match theo title nua
            when (setting.id) {
                SettingIds.WIDGET_SETTINGS -> navController.navigate(Screen.WidgetSettings.route)
                SettingIds.HOW_TO_ADD_WIDGET -> navController.navigate(Screen.HowToAddWidget.route)
                SettingIds.APPEARANCE -> navController.navigate(Screen.Appearance.route)
                SettingIds.EDIT_NAME -> showEditNameDialog = true
                SettingIds.EDIT_BIRTHDAY -> showBirthdayPicker = true
                SettingIds.SHARE_SNAPGET -> {
                    val link = inviteLink?.link
                    if (link != null) {
                        shareInviteLink(context, link)
                    } else {
                        settingsViewModel.reloadInviteLink()
                        messenger.show("Couldn't get your invite link — try again")
                    }
                }
                SettingIds.RATE_SNAPGET -> openPlayStore(context)
                SettingIds.ABOUT_TIKTOK -> openUrl(context, TIKTOK_URL)
                SettingIds.ABOUT_INSTAGRAM -> openUrl(context, INSTAGRAM_URL)
                SettingIds.ABOUT_TWITTER -> openUrl(context, TWITTER_URL)
                SettingIds.TERMS_OF_SERVICE -> navController.navigate(Screen.LegalDoc.route("terms"))
                SettingIds.PRIVACY_POLICY -> navController.navigate(Screen.LegalDoc.route("privacy"))
                SettingIds.SIGN_OUT -> authViewModel.logout()
                // Chua lam dot nay (Privacy & Safety + Delete Account) -> bao ro thay vi im lang
                else -> messenger.show("Coming soon")
            }
        },
    )

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = profile?.fullName.orEmpty(),
            isSaving = saveStatus is LoadStatus.Loading,
            onSave = { settingsViewModel.saveName(it) },
            onDismiss = { showEditNameDialog = false },
        )
    }

    if (showBirthdayPicker) {
        BirthdayPickerDialog(
            currentBirthday = profile?.birthday,
            isSaving = saveStatus is LoadStatus.Loading,
            onSave = { settingsViewModel.saveBirthday(it) },
            onDismiss = { showBirthdayPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreenContent(
    settings: List<Setting>,
    navController: NavHostController = rememberNavController(),
    onToggleChanged: (String, Boolean) -> Unit = { _, _ -> },
    onSettingClick: (Setting) -> Unit = {},
) {
    Scaffold(
        // Skin v2: nen trong suot -> thay backdrop cua skin (Navigation.kt)
        containerColor = Color.Transparent,
        topBar = {
            SettingScreenTopBar(navController)
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Group settings by type
            val groupedSettings = settings.groupBy { it.type }

            groupedSettings.forEach { (settingType, settingItems) ->
                item {
                    SettingSectionHeader(settingType = settingType)
                }

                items(settingItems) { setting ->
                    SettingItem(
                        setting = setting,
                        onToggleChanged = onToggleChanged,
                        onSettingClick = onSettingClick,
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun SettingSectionHeader(settingType: SettingType) {
    val (icon, label) = when (settingType) {
        SettingType.WIDGET -> Pair(Icons.Outlined.EmojiEmotions, "Widgets")
        SettingType.CUSTOMIZE -> Pair(Icons.Outlined.Build, "Customize")
        SettingType.GENERAL -> Pair(Icons.Outlined.Settings, "General")
        SettingType.PRIVACY_SAFETY -> Pair(Icons.Outlined.Security, "Privacy & Safety")
        SettingType.SUPPORT -> Pair(Icons.AutoMirrored.Outlined.Help, "Support")
        SettingType.ABOUT -> Pair(Icons.Outlined.Info, "About")
        SettingType.DANGER_ZONE -> Pair(Icons.Outlined.Warning, "Danger Zone")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        // (2026-11-15) Bot vang: header dung textSecondary — vang de danh cho
        // toggle dang bat + gamification (DESIGN.md 1.2). Icon trang tri -> null.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SkinTheme.colors.textSecondary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = SkinTheme.colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingItem(
    setting: Setting,
    onToggleChanged: (String, Boolean) -> Unit = { _, _ -> },
    onSettingClick: (Setting) -> Unit = {},
) {
    val isDangerZone = setting.type == SettingType.DANGER_ZONE

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDangerZone) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                glassColor()
            },
        ),
        shape = RoundedCornerShape(8.dp),
        onClick = {
            if (setting.isToggleable) {
                onToggleChanged(setting.id, !setting.isToggled)
            } else {
                onSettingClick(setting)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Setting icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isDangerZone) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        } else {
                            SkinTheme.colors.pill
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getSettingIcon(setting),
                    contentDescription = null, // tieu de da doc — tranh TalkBack doc 2 lan
                    tint = if (isDangerZone) {
                        MaterialTheme.colorScheme.error
                    } else {
                        SkinTheme.colors.textPrimary
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Setting details
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = setting.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isDangerZone) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                Text(
                    text = setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (setting.isToggleable) {
                Switch(
                    checked = setting.isToggled,
                    onCheckedChange = {
                        onToggleChanged(setting.id, it)
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Icon tung muc — map theo id ON DINH (truoc day match title nen sai gan het). */
@Composable
fun getSettingIcon(setting: Setting): ImageVector = when (setting.id) {
    SettingIds.WIDGET_SETTINGS -> Icons.Outlined.Widgets
    SettingIds.APP_ICON -> Icons.Outlined.Palette
    SettingIds.APPEARANCE -> Icons.Outlined.Palette
    SettingIds.AMBIENT_EFFECTS -> Icons.Outlined.AutoAwesome
    SettingIds.STREAK_ON_WIDGET -> Icons.Outlined.LocalFireDepartment
    SettingIds.STREAK_REMINDERS -> Icons.Outlined.NotificationsActive
    SettingIds.EDIT_NAME -> Icons.Outlined.Badge
    SettingIds.EDIT_BIRTHDAY -> Icons.Outlined.Cake
    SettingIds.HOW_TO_ADD_WIDGET -> Icons.AutoMirrored.Outlined.AddToHomeScreen
    SettingIds.BLOCKED_ACCOUNTS -> Icons.Outlined.Block
    SettingIds.ACCOUNT_VISIBILITY -> Icons.Outlined.Visibility
    SettingIds.PRIVACY_CHOICES -> Icons.Outlined.Lock
    SettingIds.REPORT_A_PROBLEM -> Icons.AutoMirrored.Outlined.Help
    SettingIds.MAKE_A_SUGGESTION -> Icons.AutoMirrored.Outlined.Help
    SettingIds.ABOUT_TIKTOK -> Icons.Outlined.MusicNote
    SettingIds.ABOUT_INSTAGRAM -> Icons.Outlined.PhotoCamera
    SettingIds.ABOUT_TWITTER -> Icons.Outlined.AlternateEmail
    SettingIds.SHARE_SNAPGET -> Icons.Outlined.Share
    SettingIds.RATE_SNAPGET -> Icons.Outlined.Star
    SettingIds.TERMS_OF_SERVICE -> Icons.Outlined.Description
    SettingIds.PRIVACY_POLICY -> Icons.Outlined.Policy
    SettingIds.DELETE_ACCOUNT -> Icons.Outlined.Delete
    SettingIds.SIGN_OUT -> Icons.AutoMirrored.Outlined.Logout
    else -> Icons.Outlined.Settings
}

/** Mo URL trong browser; khong co browser thi bao toast (khong crash). */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available on this device", Toast.LENGTH_SHORT).show()
    }
}

/** Mo trang app tren Play Store: uu tien app store, fallback web, cuoi cung toast. */
private fun openPlayStore(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri()))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Play Store is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Share sheet he thong voi link moi ket ban (cung pattern ShareYourLinkComponent). */
private fun shareInviteLink(context: Context, link: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Add me on Snapget! $link")
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share invite link"))
}
