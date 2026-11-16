package com.example.snapget.feature.coop

import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.snapget.core.constants.COOP_INVITE_TTL_SECONDS
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.ImageSetting
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.skin.skinPopupOrnament
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.navigation.Screen
import kotlinx.coroutines.delay

/** Chu ky poll loi moi khi dang o Camera/Feed (push la kenh chinh, poll la luoi an toan). */
private const val PENDING_POLL_MS = 10_000L

/** Con duoi moc nay thi dong ho + thanh tien trinh chuyen mau bao dong. */
private const val URGENT_REMAINING_MS = 60_000L

/**
 * The LOI MOI CHUP CHUNG (2026-11-16 — thay banner chu + dialog cu), dat o CA
 * Camera va Feed. Tu lo: poll 10s/lan khi man dang STARTED (push lam the hien
 * ngay — xem CoopViewModel.init), dem nguoc tung giay, het gio tu go the,
 * Accept/Decline NGAY tren the. Nhieu loi moi -> vuot ngang giua cac the.
 *
 * ⚠️ The nay nam de len camera/anh nen dung nen DAC `surface` (DESIGN.md: "moi thu
 * de len anh" giu dac), khong dung glass.
 */
@Composable
fun CoopInviteBanner(
    coopViewModel: CoopViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val pending by coopViewModel.pendingInvites.collectAsState()
    val acceptingId by coopViewModel.acceptingInviteId.collectAsState()
    val busy by coopViewModel.busy.collectAsState()

    // App xuong nen -> ngung poll; quay lai -> tai ngay (loi moi chi song 5 phut)
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                coopViewModel.loadPending()
                delay(PENDING_POLL_MS)
            }
        }
    }

    val onAccept: (PendingCoopInvite) -> Unit = { item ->
        val invite = item.invite
        val name = invite.inviterName ?: "A friend"
        coopViewModel.acceptInvite(invite.inviteId) {
            navController.navigate(
                Screen.CoopCapture.route +
                    "?inviteId=" + invite.inviteId +
                    "&name=" + Uri.encode(name),
            )
        }
    }

    // Giu danh sach khac rong cuoi cung de the con noi dung trong luc chay animation
    // thoat (luc do pending da rong)
    var lastItems by remember { mutableStateOf(emptyList<PendingCoopInvite>()) }
    if (pending.isNotEmpty() && pending != lastItems) lastItems = pending

    AnimatedVisibility(
        visible = pending.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val items = lastItems
        if (items.isEmpty()) return@AnimatedVisibility
        if (items.size == 1) {
            val item = items.first()
            CoopInviteCard(
                inviterName = item.invite.inviterName ?: "A friend",
                inviterAvatar = item.invite.inviterAvatar,
                deadlineElapsedMs = item.deadlineElapsedMs,
                accepting = acceptingId == item.invite.inviteId,
                enabled = !busy,
                onAccept = { onAccept(item) },
                onDecline = { coopViewModel.declineInvite(item.invite.inviteId) },
                onExpired = { coopViewModel.removePending(item.invite.inviteId) },
            )
        } else {
            val pagerState = rememberPagerState(pageCount = { items.size })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    key = { items[it].invite.inviteId },
                ) { page ->
                    val item = items[page]
                    CoopInviteCard(
                        inviterName = item.invite.inviterName ?: "A friend",
                        inviterAvatar = item.invite.inviterAvatar,
                        deadlineElapsedMs = item.deadlineElapsedMs,
                        accepting = acceptingId == item.invite.inviteId,
                        enabled = !busy,
                        onAccept = { onAccept(item) },
                        onDecline = { coopViewModel.declineInvite(item.invite.inviteId) },
                        onExpired = { coopViewModel.removePending(item.invite.inviteId) },
                        pageLabel = "${page + 1} of ${items.size}",
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Cham trang: bao con loi moi khac, vuot ngang de xem
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(items.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) {
                                        SkinTheme.colors.accent
                                    } else {
                                        SkinTheme.colors.textSecondary.copy(alpha = 0.5f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1 the loi moi (stateless — preview duoc). Avatar + ten + dem nguoc mm:ss + thanh
 * thoi gian con lai + 2 nut Decline / Accept. Duoi 1 phut -> dong ho chuyen mau
 * error. Dem ve 0 -> goi [onExpired] (the bi go).
 */
@Composable
fun CoopInviteCard(
    inviterName: String,
    inviterAvatar: String?,
    deadlineElapsedMs: Long,
    accepting: Boolean,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
    pageLabel: String? = null,
    totalMs: Long = COOP_INVITE_TTL_SECONDS * 1000,
) {
    val currentOnExpired by rememberUpdatedState(onExpired)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadlineElapsedMs) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (now >= deadlineElapsedMs) {
                currentOnExpired()
                break
            }
            delay(250)
        }
    }
    val remainingMs = (deadlineElapsedMs - now).coerceAtLeast(0)
    val urgent = remainingMs <= URGENT_REMAINING_MS
    val timerColor = if (urgent) SkinTheme.colors.error else SkinTheme.colors.accent
    val totalSec = (remainingMs + 999) / 1000 // lam tron len: khong hien 0:00 khi con <1s
    val timerText = "%d:%02d left".format(totalSec / 60, totalSec % 60)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SkinTheme.colors.surface,
        border = BorderStroke(2.dp, SkinTheme.colors.accent),
        shadowElevation = 8.dp,
        // Thong bao moi coop: dai ornament manh 14dp (= padding tren) — 2026-11-16
        modifier = modifier
            .fillMaxWidth()
            .skinPopupOrnament(SkinTheme.colors.surface, RoundedCornerShape(20.dp), band = 14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Circle(
                    outerSize = 46.dp,
                    gap = 2.dp,
                    backgroundColor = SkinTheme.colors.pill,
                    borderColor = SkinTheme.colors.accent,
                    onClick = {},
                    imageSetting = ImageSetting(
                        imageUrl = avatarOrDefault(inviterAvatar, inviterName),
                        contentDescription = inviterName,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inviterName,
                        color = SkinTheme.colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "wants to snap a co-op photo with you 📸",
                        color = SkinTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                if (pageLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pageLabel,
                        color = SkinTheme.colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Dem nguoc: icon + mm:ss + thanh thoi gian con lai
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = timerColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timerText,
                    color = timerColor,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) },
                    color = timerColor,
                    trackColor = SkinTheme.colors.pill,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = enabled,
                    shape = SkinTheme.shapes.pill,
                    border = BorderStroke(1.dp, SkinTheme.colors.textSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                ) {
                    Text(text = "Decline", color = SkinTheme.colors.textPrimary)
                }
                Button(
                    onClick = onAccept,
                    enabled = enabled,
                    shape = SkinTheme.shapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkinTheme.colors.accent,
                        contentColor = SkinTheme.colors.onAccent,
                        disabledContainerColor = SkinTheme.colors.accent.copy(alpha = 0.6f),
                        disabledContentColor = SkinTheme.colors.onAccent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                ) {
                    if (accepting) {
                        CircularProgressIndicator(
                            color = SkinTheme.colors.onAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(text = "Accept", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
