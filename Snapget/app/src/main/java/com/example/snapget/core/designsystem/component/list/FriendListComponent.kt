package com.example.snapget.core.designsystem.component.list

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.snapget.R
import com.example.snapget.core.constants.MAX_FRIENDS
import com.example.snapget.core.constants.MeasurementConfig
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.button.ShowMoreShowLessButton
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.IconSetting
import com.example.snapget.core.designsystem.component.container.BlurredContainer
import com.example.snapget.core.designsystem.skin.SkinIcon
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.model.User
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.trimUsername
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// Generic interface for list items
interface ListItem {
    val id: String
    val displayName: String
    val imageUrl: String?
}

// Extension for User to implement ListItem
fun User.asListItem(): ListItem = object : ListItem {
    override val id: String = this@asListItem.id
    override val displayName: String = this@asListItem.username
    override val imageUrl: String? = this@asListItem.avatar.ifEmpty { null }
}

// Extension for ThirdPartyApp to implement ListItem
fun ThirdPartyApp.asListItem(): ListItem = object : ListItem {
    override val id: String = this@asListItem.name
    override val displayName: String = this@asListItem.name
    override val imageUrl: String? = null
}

@Composable
fun <T> GenericCircleList(
    items: List<T>,
    selectedItemId: String = "",
    onItemSelected: (T) -> Unit = {},
    addEveryoneOption: Boolean = false,
    addCurrentUserOption: Boolean = false,
    currentUser: T? = null,
    itemToListItem: (T) -> ListItem,
    itemContent: @Composable (T, Boolean) -> Unit,
) {
    val finalItems = remember(items, currentUser, addEveryoneOption, addCurrentUserOption) {
        val result = mutableListOf<T?>()

        // Ban be truoc
        result.addAll(items)

        // Add current user option if requested
        if (addCurrentUserOption && currentUser != null) {
            result.add(currentUser)
        }

        // "Everyone" nam CUOI danh sach (yeu cau UX 2026-07-19)
        if (addEveryoneOption) {
            @Suppress("UNCHECKED_CAST")
            result.add(createEveryoneItem<T>() as T?)
        }

        result.filterNotNull()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .padding(end = 16.dp),
    ) {
        finalItems.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                itemContent(item, itemToListItem(item).id == selectedItemId)

                Text(
                    text = trimUsername(itemToListItem(item).displayName),
                    color = SkinTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> createEveryoneItem(): T = User(id = "everyone", username = "Everyone", avatar = "") as T

@Composable
fun FriendList(
    user: User? = null,
    friends: List<User> = emptyList(),
    selectedFriendId: String = "everyone",
    onFriendSelected: (User) -> Unit = {},
) {
    GenericCircleList(
        items = friends,
        selectedItemId = selectedFriendId,
        onItemSelected = onFriendSelected,
        addEveryoneOption = true,
        addCurrentUserOption = true,
        currentUser = user?.copy(id = "you", username = "You"),
        itemToListItem = { it.asListItem() },
        itemContent = { friend, isSelected ->
            FriendItem(
                user = friend,
                isSelected = isSelected,
                onClick = { onFriendSelected(friend) },
            )
        },
    )
}

@Composable
fun ThirdPartyAppList(
    apps: List<ThirdPartyApp> = emptyList(),
    onAppSelected: (ThirdPartyApp) -> Unit = {},
) {
    GenericCircleList(
        items = apps,
        onItemSelected = onAppSelected,
        itemToListItem = { it.asListItem() },
        itemContent = { app, isSelected ->
            ThirdPartyAppItem(
                app = app,
                isSelected = isSelected,
                onClick = { onAppSelected(app) },
            )
        },
    )
}

@Composable
fun FriendItem(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Circle(
        outerSize = 56.dp,
        gap = 5.dp,
        backgroundColor = SkinTheme.colors.pill,
        borderColor = if (isSelected) SkinTheme.colors.accent else SkinTheme.colors.onSurfaceVariant,
        onClick = onClick,
        innerContent = {
            AsyncImage(
                model = avatarOrDefault(user.avatar, user.username),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
            )
        },
    )
}

@Composable
fun ThirdPartyAppItem(
    app: ThirdPartyApp,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // Icon app ngoai = drawable LOCAL (truoc tai URL wikimedia/icons8 -> hay loi, mat icon)
    Circle(
        outerSize = 56.dp,
        gap = 1.dp,
        backgroundColor = Color.White,
        borderColor = if (isSelected) SkinTheme.colors.accent else SkinTheme.colors.pill,
        onClick = onClick,
        innerContent = {
            Image(
                painter = painterResource(app.icon),
                contentDescription = app.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/** Kenh chia se link moi ket ban: mo thang app dich, khong co app -> share chooser he thong. */
enum class ShareTarget { MESSENGER, INSTAGRAM, SMS, OTHER }

data class ThirdPartyApp(
    val name: String,
    @DrawableRes val icon: Int,
    val target: ShareTarget,
)

val listThirdPartyApp = listOf(
    ThirdPartyApp(name = "Messenger", icon = R.drawable.fb, target = ShareTarget.MESSENGER),
    ThirdPartyApp(name = "Instagram", icon = R.drawable.insta, target = ShareTarget.INSTAGRAM),
    ThirdPartyApp(name = "Messages", icon = R.drawable.message, target = ShareTarget.SMS),
    ThirdPartyApp(name = "Others", icon = R.drawable.ic_connection, target = ShareTarget.OTHER),
)

private const val PACKAGE_MESSENGER = "com.facebook.orca"
private const val PACKAGE_INSTAGRAM = "com.instagram.android"
private const val LINK_LOADING_MESSAGE = "Your invite link is still loading. Try again in a moment."

/**
 * Chia se link moi ket ban toi [target]. App dich chua cai (ActivityNotFound) -> lui ve
 * share chooser. Link chua tai xong -> bao nhe. Dung Toast (khong phai AppMessenger) vi
 * sheet la cua so modal rieng, Snackbar cua MainActivity bi che phia sau.
 */
fun shareInviteLink(context: Context, link: String?, target: ShareTarget = ShareTarget.OTHER) {
    if (link.isNullOrBlank()) {
        Toast.makeText(context, LINK_LOADING_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    val text = "Add me on Snapget! $link"
    fun sendText() = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendText(), "Share invite link")
    val direct: Intent? = when (target) {
        ShareTarget.MESSENGER -> sendText().setPackage(PACKAGE_MESSENGER)
        ShareTarget.INSTAGRAM -> sendText().setPackage(PACKAGE_INSTAGRAM)
        ShareTarget.SMS -> Intent(Intent.ACTION_SENDTO, "smsto:".toUri()).putExtra("sms_body", text)
        ShareTarget.OTHER -> null
    }
    try {
        context.startActivity(direct ?: chooser)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(chooser)
    }
}

/** Copy link moi vao clipboard (Android 13+ tu hien popup he thong, duoi 13 -> Toast). */
fun copyInviteLink(context: Context, link: String?) {
    if (link.isNullOrBlank()) {
        Toast.makeText(context, LINK_LOADING_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Snapget invite link", link))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Invite link copied", Toast.LENGTH_SHORT).show()
    }
}

/** "Find friends from other apps": bam icon app -> gui link moi qua app do. */
@Composable
fun ExternalAppComponent(inviteLink: String? = null) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(icon = Icons.Outlined.TravelExplore, title = "Find friends from other apps")

        BlurredContainer {
            ThirdPartyAppList(
                apps = listThirdPartyApp,
                onAppSelected = { app -> shareInviteLink(context, inviteLink, app.target) },
            )
        }
    }
}

/** Tieu de section dung chung trong sheet ban be: icon + chu dam (+ phu de mo tuy chon). */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    trailing: String? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SkinTheme.colors.textPrimary,
            modifier = Modifier.size(MeasurementConfig.USER_DETAIL_BOTTOM_SHEET_TRAILING_ICON_SIZE),
        )
        Text(
            text = title,
            color = SkinTheme.colors.textPrimary,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.Bold,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = SkinTheme.colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
fun YourFriendAppComponent(
    friends: List<FriendUi> = emptyList(),
    onRemoveFriend: (FriendUi) -> Unit = {},
    onFriendClick: (FriendUi) -> Unit = {},
    onMessageClick: (FriendUi) -> Unit = {},
    isLoading: Boolean = false,
    initialShowCount: Int = 3,
) {
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Loc theo ten (khong phan biet hoa thuong); dang tim -> hien het ket qua, bo "Show more"
    val filtered = remember(friends, query) {
        if (query.isBlank()) friends else friends.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val friendsToShow = if (showAll || query.isNotBlank() || filtered.size <= initialShowCount) {
        filtered
    } else {
        filtered.take(initialShowCount)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SectionHeader(
            icon = Icons.Outlined.Group,
            title = "Your friends",
            trailing = if (friends.isNotEmpty()) "(${friends.size})" else null,
        )

        // O tim kiem chi hien khi danh sach du dai de can loc
        if (friends.size > initialShowCount) {
            FriendSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = SkinTheme.colors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            friends.isEmpty() -> {
                Text(
                    text = "You don't have any friends yet. Share your link to invite someone!",
                    color = SkinTheme.colors.textPrimary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            filtered.isEmpty() -> {
                Text(
                    text = "No friends match \"${query.trim()}\"",
                    color = SkinTheme.colors.textPrimary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            else -> {
                // Column (khong Lazy) vi sheet cha da verticalScroll; toi da 20 ban
                Column {
                    friendsToShow.forEach { friend ->
                        FriendListItem(
                            friend = friend,
                            onClick = { onFriendClick(friend) },
                            onMessageClick = { onMessageClick(friend) },
                            onRemoveFriend = onRemoveFriend,
                        )
                    }
                }

                if (query.isBlank() && filtered.size > initialShowCount) {
                    ShowMoreShowLessButton(
                        showAll = showAll,
                        totalCount = filtered.size,
                        visibleCount = friendsToShow.size,
                        onToggle = { showAll = !showAll },
                    )
                }
            }
        }
    }
}

/** O tim ban theo ten — nen `surface`, bo goc `input` theo skin. */
@Composable
private fun FriendSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.input)
            .background(SkinTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = SkinTheme.colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search friends",
                    color = SkinTheme.colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = SkinTheme.colors.textPrimary),
                cursorBrush = SolidColor(SkinTheme.colors.textPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear search",
                tint = SkinTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * 1 hang ban: bam hang -> mo profile ban; icon chat -> nhan tin; icon xoa -> hoi xac nhan
 * (dialog do sheet cha hien). Nut 40dp cho de bam (truoc 24dp, icon X de bam nham).
 */
@Composable
fun FriendListItem(
    friend: FriendUi,
    onRemoveFriend: (FriendUi) -> Unit,
    onClick: () -> Unit = {},
    onMessageClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar: chua co anh -> avatar mac dinh theo ten (truoc de trong khi avatar rong)
        Circle(
            outerSize = 56.dp,
            gap = 5.dp,
            backgroundColor = SkinTheme.colors.pill,
            borderColor = SkinTheme.colors.onSurfaceVariant,
            onClick = onClick,
            innerContent = {
                AsyncImage(
                    model = avatarOrDefault(friend.avatar, friend.name),
                    contentDescription = "Avatar ${friend.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )

        Text(
            text = friend.name,
            color = SkinTheme.colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        IconButton(
            onClick = onMessageClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = "Message ${friend.name}",
                tint = SkinTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        IconButton(
            onClick = { onRemoveFriend(friend) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonRemove,
                contentDescription = "Remove ${friend.name}",
                tint = SkinTheme.colors.error,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun YourFriendAppComponentPreview() {
    YourFriendAppComponent(
        friends = listOf(
            FriendUi(id = "1", name = "An Nguyen"),
            FriendUi(id = "2", name = "Binh Tran"),
            FriendUi(id = "3", name = "Chi Le"),
        ),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun HorizontalShowMoreComponent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // line trái
        HorizontalDivider(
            color = SkinTheme.colors.textPrimary.copy(alpha = 0.3f),
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Text + icon group
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    color = Color.DarkGray,
                    shape = SkinTheme.shapes.pill,
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Show more",
                color = SkinTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // line phải
        HorizontalDivider(
            color = SkinTheme.colors.textPrimary.copy(alpha = 0.3f),
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        )
    }
}

/**
 * Section chia se link moi ket ban: moi hang mo dung app dich (khong co -> share chooser),
 * hang cuoi "Copy link" chep link vao clipboard.
 */
@Composable
fun ShareYourLinkComponent(
    inviteLink: String? = null,
    inviteExpiresAt: String? = null,
    isLinkFailed: Boolean = false,
    onRetryLink: () -> Unit = {},
    onShowQr: () -> Unit = {},
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(icon = Icons.Outlined.Upload, title = "Share your Snapget link")

        InviteLinkCard(
            inviteLink = inviteLink,
            expiresAt = inviteExpiresAt,
            isFailed = isLinkFailed,
            onCopy = { copyInviteLink(context, inviteLink) },
            onShowQr = onShowQr,
            onRetry = onRetryLink,
        )

        listThirdPartyApp.forEach { app ->
            ShareRow(
                label = app.name,
                onClick = { shareInviteLink(context, inviteLink, app.target) },
                leading = {
                    ThirdPartyAppItem(
                        app = app,
                        isSelected = false,
                        onClick = { shareInviteLink(context, inviteLink, app.target) },
                    )
                },
            )
        }

        ShareRow(
            label = "Copy link",
            onClick = { copyInviteLink(context, inviteLink) },
            leading = {
                Circle(
                    outerSize = 56.dp,
                    gap = 14.dp,
                    backgroundColor = SkinTheme.colors.pill,
                    borderColor = SkinTheme.colors.onSurfaceVariant,
                    onClick = { copyInviteLink(context, inviteLink) },
                    iconSetting = IconSetting(icon = Icons.Outlined.ContentCopy, contentDescription = null),
                )
            },
        )
    }
}

/**
 * The link moi cua minh: link rut gon (bo https://), han con lai (link song 30 ngay,
 * het han server tu sinh ma moi), nut Copy + QR. Dang tai -> spinner; loi -> Retry.
 */
@Composable
private fun InviteLinkCard(
    inviteLink: String?,
    expiresAt: String?,
    isFailed: Boolean,
    onCopy: () -> Unit,
    onShowQr: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.input)
            .background(SkinTheme.colors.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            inviteLink != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = SkinTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = inviteLink.removePrefix("https://").removePrefix("http://"),
                        color = SkinTheme.colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                inviteExpiryLabel(expiresAt)?.let { label ->
                    Text(
                        text = label,
                        color = SkinTheme.colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinkCardButton(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copy",
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    )
                    LinkCardButton(
                        icon = Icons.Outlined.QrCode2,
                        label = "QR code",
                        onClick = onShowQr,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            isFailed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Couldn't load your invite link.",
                        color = SkinTheme.colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    LinkCardButton(icon = Icons.Outlined.Refresh, label = "Retry", onClick = onRetry)
                }
            }
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        color = SkinTheme.colors.textPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Generating your invite link...",
                        color = SkinTheme.colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkCardButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(SkinTheme.shapes.pill)
            .background(SkinTheme.colors.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SkinTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = SkinTheme.colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * "Expires in N days" tu ISO expiresAt — chi doc phan ngay (UTC) bang SimpleDateFormat
 * (tranh java.time vi minSdk 24). Loi parse -> null (an dong han).
 */
internal fun inviteExpiryLabel(iso: String?): String? {
    val datePart = iso?.take(10) ?: return null
    val expiry = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(datePart)
    } catch (_: ParseException) {
        null
    } ?: return null
    val msLeft = expiry.time - System.currentTimeMillis()
    val daysLeft = TimeUnit.MILLISECONDS.toDays(msLeft).toInt()
    return when {
        msLeft <= 0 -> "Expired — a new link will be generated"
        daysLeft <= 0 -> "Expires today"
        daysLeft == 1 -> "Expires in 1 day"
        else -> "Expires in $daysLeft days"
    }
}

@Composable
private fun ShareRow(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        leading()
        Text(
            text = label,
            color = SkinTheme.colors.textPrimary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        SkinIcon(
            res = SkinTheme.icons.chevronRight,
            fallback = Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = SkinTheme.colors.textPrimary,
            modifier = Modifier.size(MeasurementConfig.USER_DETAIL_BOTTOM_SHEET_TRAILING_ICON_SIZE),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun FriendListPreview() {
    FriendList(
        user = SampleData.users.firstOrNull { it.id == "kai_tanaka" },
        friends = SampleData.users.filter { it.id != "kai_tanaka" },
        selectedFriendId = "everyone",
        onFriendSelected = { friend ->
            println("Selected friend: ${friend.username}")
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun GenericListExamples() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Friends List:", color = SkinTheme.colors.textPrimary)
        FriendList(
            user = SampleData.users.firstOrNull { it.id == "kai_tanaka" },
            friends = SampleData.users.filter { it.id != "kai_tanaka" },
            selectedFriendId = "everyone",
        )

        Text("Third Party Apps:", color = SkinTheme.colors.textPrimary)
        BlurredContainer {
            ThirdPartyAppList(
                apps = listThirdPartyApp,
            )
        }
    }
}

@Composable
fun TotalFriendComponent(
    totalFriends: Int,
    // `modifier` phai la tham so TUY CHON DAU TIEN (quy uoc Compose) — de nguoi
    // goi truyen duoc theo vi tri ma khong phai dat ten moi tham so truoc no
    modifier: Modifier = Modifier,
    maxFriends: Int = MAX_FRIENDS,
    onAddFriendClick: () -> Unit = {},
) {
    val isFull = totalFriends >= maxFriends
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$totalFriends out of $maxFriends friends",
            color = SkinTheme.colors.textPrimary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleLarge,
        )

        // Day 20 ban -> nhac phai xoa bot moi ket ban moi duoc (rule PDF)
        Text(
            text = if (isFull) "Your friend list is full. Remove a friend to add someone new." else "Invite a friend to continue",
            color = if (isFull) SkinTheme.colors.error else SkinTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SkinTheme.shapes.image)
                .background(SkinTheme.colors.pill)
                .clickable { onAddFriendClick() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonAdd,
                contentDescription = null,
                tint = SkinTheme.colors.textPrimary,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "Add new friend",
                color = SkinTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun TotalFriendComponentPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        TotalFriendComponent(totalFriends = 5, maxFriends = 20)

        // Example with no friends
        TotalFriendComponent(totalFriends = 0, maxFriends = 20)
    }
}
