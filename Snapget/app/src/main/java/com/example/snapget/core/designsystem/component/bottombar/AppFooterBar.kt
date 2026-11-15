package com.example.snapget.core.designsystem.component.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.navigation.Screen

private enum class FooterTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isCenter: Boolean = false,
) {
    QUEST(
        route = Screen.DailyQuest.route,
        label = "Quest",
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
    ),
    CHAT(
        route = Screen.Message.route,
        label = "Chat",
        selectedIcon = Icons.AutoMirrored.Filled.Send,
        unselectedIcon = Icons.AutoMirrored.Outlined.Send,
    ),
    HOME(
        route = Screen.Camera.route,
        label = "Home",
        selectedIcon = Icons.Default.Add,
        unselectedIcon = Icons.Default.Add,
        isCenter = true,
    ),
    SHOP(
        route = Screen.Shop.route,
        label = "Shop",
        selectedIcon = Icons.Filled.ShoppingBag,
        unselectedIcon = Icons.Outlined.ShoppingBag,
    ),
    PROFILE(
        route = Screen.Profile.route,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    ),
}

/**
 * Thanh footer 5 tab chinh: Quest - Chat - Home - Shop - Profile.
 * Mau active/inactive doc tu [SkinTheme.colors] de dong bo khi doi skin.
 */
@Composable
fun AppFooterBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    unreadMessages: Int = 0,
) {
    val activeColor = SkinTheme.colors.navBarActive
    val inactiveColor = SkinTheme.colors.navBarInactive

    Surface(
        color = SkinTheme.colors.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterTab.entries.forEach { tab ->
                val isSelected = when (tab) {
                    FooterTab.HOME -> currentRoute == Screen.Camera.route || currentRoute == Screen.Post.route
                    FooterTab.PROFILE -> currentRoute?.startsWith(Screen.Profile.route) == true
                    else -> currentRoute == tab.route
                }

                if (tab.isCenter) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SkinTheme.colors.surfaceVariant)
                            .clickable { onTabSelected(tab.route) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.selectedIcon,
                            contentDescription = tab.label,
                            tint = if (isSelected) activeColor else inactiveColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                } else {
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onTabSelected(tab.route) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val iconModifier = if (tab == FooterTab.CHAT) {
                            Modifier
                                .size(26.dp)
                                .rotate(-25f)
                        } else {
                            Modifier.size(26.dp)
                        }

                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = if (isSelected) activeColor else inactiveColor,
                            modifier = iconModifier,
                        )

                        if (tab == FooterTab.CHAT && unreadMessages > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 4.dp)
                                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (unreadMessages > 99) "99+" else unreadMessages.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
