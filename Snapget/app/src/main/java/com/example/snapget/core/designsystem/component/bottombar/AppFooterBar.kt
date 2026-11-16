package com.example.snapget.core.designsystem.component.bottombar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.feedback.rememberFeedback
import com.example.snapget.core.designsystem.motion.MotionTokens
import com.example.snapget.core.designsystem.motion.popOnChange
import com.example.snapget.core.designsystem.motion.pressScale
import com.example.snapget.core.designsystem.motion.rememberReducedMotion
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
        // Nhan TalkBack "Camera" (icon "+" = tao moment) — truoc la "Home" gay nham
        label = "Camera",
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

private val IconSizeIdle = 26.dp
private val IconSizeSelected = 32.dp

/**
 * Thanh footer 5 tab chinh: Quest - Chat - Home - Shop - Profile.
 * Mau active/inactive doc tu [SkinTheme.colors] de dong bo khi doi skin.
 *
 * (2026-11-15 Playful v2 — user chot) CHI icon, khong nhan chu. Tab dang chon:
 * doi mau + PHINH TO 26 -> 32dp bang spring nay. Moi tab co ripple + nhan-lun +
 * haptic (truoc `indication = null` -> bam khong thay gi), semantics `Role.Tab` +
 * selected. Badge chua doc dung token `badge` (= accentGold, bo do hardcode).
 */
@Composable
fun AppFooterBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    unreadMessages: Int = 0,
) {
    val feedback = rememberFeedback()

    Surface(
        // Skin v2: 90% -> backdrop cua skin hoi lo qua footer, van doc ro icon
        color = SkinTheme.colors.background.copy(alpha = 0.9f),
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
                FooterTabItem(
                    tab = tab,
                    isSelected = isSelected,
                    unreadMessages = if (tab == FooterTab.CHAT) unreadMessages else 0,
                    onClick = {
                        if (!isSelected) feedback.tap()
                        onTabSelected(tab.route)
                    },
                )
            }
        }
    }
}

@Composable
private fun FooterTabItem(
    tab: FooterTab,
    isSelected: Boolean,
    unreadMessages: Int,
    onClick: () -> Unit,
) {
    val colors = SkinTheme.colors
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (isSelected) colors.navBarActive else colors.navBarInactive,
        label = "tabTint",
    )
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) IconSizeSelected else IconSizeIdle,
        animationSpec = if (reduced) spring(stiffness = 10_000f) else spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "tabSize",
    )
    val a11yLabel = if (unreadMessages > 0) "${tab.label}, $unreadMessages unread" else tab.label

    val selectableModifier = Modifier
        .selectable(
            selected = isSelected,
            interactionSource = interaction,
            indication = ripple(bounded = false, radius = 28.dp),
            role = Role.Tab,
            onClick = onClick,
        )
        .semantics { contentDescription = a11yLabel }
        .pressScale(interaction, pressedScale = MotionTokens.PRESSED_SCALE)

    if (tab.isCenter) {
        // Nut giua: khoi bo goc nen surfaceVariant chua dau "+" — vung cham 56x48dp
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 48.dp)
                .then(selectableModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.accent else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.selectedIcon,
                    contentDescription = null,
                    tint = if (isSelected) colors.onAccent else colors.navBarInactive,
                    modifier = Modifier.size(iconSize - 2.dp),
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(selectableModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .then(if (tab == FooterTab.CHAT) Modifier.rotate(-25f) else Modifier),
            )

            if (unreadMessages > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 4.dp)
                        .popOnChange(unreadMessages)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(colors.badge)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadMessages > 99) "99+" else unreadMessages.toString(),
                        color = colors.onBadge,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
