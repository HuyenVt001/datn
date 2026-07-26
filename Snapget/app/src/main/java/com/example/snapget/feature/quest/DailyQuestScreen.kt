package com.example.snapget.feature.quest

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.TodayQuestDto

// Vang gamification (streak/quest/badge) — theo DESIGN.md muc 2, khong them hex vang khac
private val SnapGold = Color(0xFFFFD700)
private val SubtleGray = Color(0xFFB0B0B0)
private val PillOlive = Color(0xFF404137)

/**
 * Man Daily Quest: 2 quest co dinh cua ngay (server tu hoan thanh) + bo suu tap khung.
 * Entry: nut cup 🏆 tren top bar PostScreen (user chot 2026-07-13).
 */
@Composable
fun DailyQuestScreen(
    navController: NavController,
    viewModel: QuestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Vao man = goi /quests/today -> server tu hoan thanh quest LOGIN
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            SimpleTopBar(title = "Daily Quest", onBackClick = { navController.popBackStack() })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (uiState.status) {
            is LoadStatus.Init, is LoadStatus.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = SnapGold)
                }
            }

            is LoadStatus.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.status.description,
                        color = SubtleGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    TextButton(onClick = { viewModel.load() }) {
                        Text(text = "Retry", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            else -> QuestContent(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun QuestContent(uiState: QuestUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StreakBanner(streak = uiState.personalStreak) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's quests",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${uiState.completedCount}/${uiState.quests.size}",
                    color = SnapGold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        items(uiState.quests.size) { index ->
            QuestCard(quest = uiState.quests[index])
        }

        // Banner khung vua duoc thuong hom nay (xong 2/2 quest)
        val rewardFrame = uiState.frames.find { it.frameId == uiState.rewardFrameId }
        if (rewardFrame != null) {
            item { RewardBanner(frame = rewardFrame) }
        }

        item {
            Text(
                text = "Frame collection",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (uiState.frames.isEmpty()) {
            item {
                Text(
                    text = "No frames yet — complete quests to earn them!",
                    color = SubtleGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            // Luoi 3 cot: chia frames thanh tung hang de nam trong LazyColumn
            items(uiState.frames.chunked(3).size) { rowIndex ->
                val rowFrames = uiState.frames.chunked(3)[rowIndex]
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowFrames.forEach { frame ->
                        FrameItem(
                            frame = frame,
                            isNewReward = frame.frameId == uiState.rewardFrameId,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Bu cho o trong de hang cuoi khong gian ra
                    repeat(3 - rowFrames.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Banner streak ca nhan — vang gamification. */
@Composable
private fun StreakBanner(streak: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, SnapGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🔥", fontSize = 34.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "$streak",
                color = SnapGold,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (streak == 1) "day streak" else "days streak",
                color = SubtleGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Reach 3 · 7 · 14 · 30\nto unlock frames",
            color = SubtleGray,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            lineHeight = 16.sp,
        )
    }
}

/** The 1 quest: icon tron + noi dung (tieng Viet tu server) + trang thai. */
@Composable
private fun QuestCard(quest: TodayQuestDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PillOlive),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (quest.type == "POST_MOMENT") "📸" else "👋",
                fontSize = 22.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quest.content,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (quest.completed) "Done today" else "Not done yet",
                color = if (quest.completed) SnapGold else SubtleGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(
            imageVector = if (quest.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (quest.completed) "Completed" else "Not completed",
            tint = if (quest.completed) SnapGold else SubtleGray,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Banner "vua mo khoa khung moi" khi xong 2/2 quest hom nay. */
@Composable
private fun RewardBanner(frame: FrameDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, SnapGold, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🎁", fontSize = 30.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "New frame unlocked!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = frame.frameName,
                color = SnapGold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (frame.imageUrl != null) {
            AsyncImage(
                model = frame.imageUrl,
                contentDescription = frame.frameName,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/** 1 o khung trong bo suu tap: khoa = mo + 🔒, moc streak = nhan 🔥. */
@Composable
private fun FrameItem(
    frame: FrameDto,
    isNewReward: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (frame.imageUrl != null) {
                AsyncImage(
                    model = frame.imageUrl,
                    contentDescription = frame.frameName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .alpha(if (frame.isUnlocked) 1f else 0.35f),
                )
            }
            if (!frame.isUnlocked) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🔒", fontSize = 22.sp)
                }
            }
            if (isNewReward) {
                Text(
                    text = "NEW",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SnapGold)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = frame.frameName,
            color = if (frame.isUnlocked) Color.White else SubtleGray,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (frame.milestone != null) {
            Text(
                text = "🔥 ${frame.milestone}d",
                color = SnapGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
