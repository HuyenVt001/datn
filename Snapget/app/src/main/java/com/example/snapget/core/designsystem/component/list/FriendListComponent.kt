package com.example.snapget.core.designsystem.component.list

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.snapget.R
import com.example.snapget.core.constants.MeasurementConfig
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.button.ShowMoreShowLessButton
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.ImageSetting
import com.example.snapget.core.designsystem.component.container.BlurredContainer
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.model.User
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.trimUsername

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
                    color = Color.White,
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
        backgroundColor = Color(0xFF404137),
        borderColor = if (isSelected) Color.Yellow else Color(0xFFB8B8B8),
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
    Circle(
        outerSize = 56.dp,
        gap = 1.dp,
        backgroundColor = Color.Gray,
        borderColor = if (isSelected) Color.Yellow else Color(0xFF404137),
        onClick = onClick,
        imageSetting = ImageSetting(
            imageUrl = app.imageUrl,
        ),
    )
}

// Keep your original data classes
data class ThirdPartyApp(
    val name: String,
    val imageUrl: String = "",
    val icon: Int,
    val onClick: () -> Unit,
)

val listThirdPartyApp = listOf(
    ThirdPartyApp(
        name = "Messenger",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/be/Facebook_Messenger_logo_2020.svg/512px-Facebook_Messenger_logo_2020.svg.png",
        icon = R.drawable.fb,
        onClick = {},
    ),
    ThirdPartyApp(
        name = "Instagram",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Instagram_logo_2016.svg/2048px-Instagram_logo_2016.svg.png",
        icon = R.drawable.insta,
        onClick = {},
    ),
    ThirdPartyApp(
        name = "Messages",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/51/IMessage_logo.svg/234px-IMessage_logo.svg.png",
        icon = R.drawable.message,
        onClick = {},
    ),
    ThirdPartyApp(
        name = "Others",
        imageUrl = "https://img.icons8.com/windows/50/link.png",
        icon = R.drawable.ic_connection,
        onClick = {},
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun ExternalAppComponent() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(MeasurementConfig.USER_DETAIL_BOTTOM_SHEET_TRAILING_ICON_SIZE),
            )

            Text(
                text = "Find Friend From other Apps",
                color = Color.White,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.Bold,
            )
        }

        BlurredContainer {
            ThirdPartyAppList(
                apps = listThirdPartyApp,
                onAppSelected = { app ->
                    println("Selected app: ${app.name}")
                },
            )
        }
    }
}

@Composable
fun YourFriendAppComponent(
    friends: List<FriendUi> = emptyList(),
    onRemoveFriend: (FriendUi) -> Unit = {},
    isLoading: Boolean = false,
    initialShowCount: Int = 3,
) {
    var showAll by remember { mutableStateOf(false) }

    // Determine how many friends to show
    val friendsToShow = if (showAll || friends.size <= initialShowCount) {
        friends
    } else {
        friends.take(initialShowCount)
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "Friends",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )

            Text(
                text = "Your Friends",
                color = Color.White,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.Bold,
            )
        }

        when {
            isLoading -> {
                // Show loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            friends.isEmpty() -> {
                Text(
                    text = "You don't have any friends yet",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            else -> {
                // Friends list - use Column since parent is already scrollable
                Column {
                    friendsToShow.forEach { friend ->
                        FriendListItem(
                            friend = friend,
                            onRemoveFriend = onRemoveFriend,
                        )
                    }
                }

                // Show More/Show Less button
                if (friends.size > initialShowCount) {
                    ShowMoreShowLessButton(
                        showAll = showAll,
                        totalCount = friends.size,
                        visibleCount = friendsToShow.size,
                        onToggle = { showAll = !showAll },
                    )
                }
            }
        }
    }
}

@Composable
fun FriendListItem(
    friend: FriendUi,
    onRemoveFriend: (FriendUi) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(12.dp)
            .clickable { /* Navigate to friend's profile */ },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        // Friend's avatar
        Circle(
            outerSize = 56.dp,
            gap = 5.dp,
            backgroundColor = Color(0xFF404137),
            onClick = {},
            imageSetting = ImageSetting(
                imageUrl = friend.avatar,
            ),
        )

        // Friend's name
        Text(
            text = friend.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Friend streak chung — gamification dung vang gold (DESIGN.md muc 2)
        if (friend.streak > 0) {
            Text(
                text = "🔥 ${friend.streak}",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Remove friend button
        IconButton(
            onClick = { onRemoveFriend(friend) },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove Friend",
                tint = Color.White,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun YourFriendAppComponentPreview() {
    YourFriendAppComponent(
        friends = listOf(
            FriendUi(id = "1", name = "An Nguyen", streak = 5),
            FriendUi(id = "2", name = "Binh Tran", streak = 0),
            FriendUi(id = "3", name = "Chi Le", streak = 12),
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
            color = Color.White.copy(alpha = 0.3f),
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
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Show more",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // line phải
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        )
    }
}

/** Section chia se link moi ket ban — moi hang mo share chooser cua he thong. */
@Composable
fun ShareYourLinkComponent(inviteLink: String? = null) {
    val context = LocalContext.current

    // Mo share sheet he thong voi link moi (nguoi nhan bam link -> deep link ket ban)
    fun shareLink() {
        val link = inviteLink ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Add me on Snapget! $link")
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share invite link"))
    }

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = "Friends",
                tint = Color.White,
                modifier = Modifier.size(MeasurementConfig.USER_DETAIL_BOTTOM_SHEET_TRAILING_ICON_SIZE),
            )

            Text(
                text = "Share your Snapget link",
                color = Color.White,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.Bold,
            )
        }

        val index = listThirdPartyApp.indexOfFirst { it.name == "Instagram" }
        val instagram = listThirdPartyApp[index]

        val uiList = listThirdPartyApp.toMutableList().apply {
            add(index + 1, instagram.copy(name = "Instagram Story"))
            this[index] = instagram.copy(name = "Instagram DMs")
        }

        uiList.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { shareLink() },
            ) {
                Circle(
                    outerSize = 56.dp,
                    gap = 5.dp,
                    backgroundColor = Color(0xFF404137),
                    borderColor = Color.Gray,
                    onClick = { shareLink() },
                    imageSetting = ImageSetting(
                        imageUrl = item.imageUrl,
                        contentDescription = "Example Image",
                    ),
                )

                // Username text with special handling for "Everyone" and "You"
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium,
                )

                // Flexible spacer to push the arrow to the end
                Spacer(modifier = Modifier.weight(1f))

                // Arrow icon (always visible)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Select",
                    tint = Color.White,
                    modifier = Modifier.size(MeasurementConfig.USER_DETAIL_BOTTOM_SHEET_TRAILING_ICON_SIZE),
                )
            }
        }
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
        Text("Friends List:", color = Color.White)
        FriendList(
            user = SampleData.users.firstOrNull { it.id == "kai_tanaka" },
            friends = SampleData.users.filter { it.id != "kai_tanaka" },
            selectedFriendId = "everyone",
        )

        Text("Third Party Apps:", color = Color.White)
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
    maxFriends: Int = 20,
    onAddFriendClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$totalFriends out of $maxFriends friends",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = "Invite a friend to continue",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
        )
        Box(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onAddFriendClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.DarkGray),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Friends",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = "Add new friend",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
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
