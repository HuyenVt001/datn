package com.example.snapget.feature.quest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.snapget.R
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.TodayQuestDto
import com.example.snapget.navigation.Screen

// Mau lay tu token cua skin (SkinTheme.colors.*) — 3 hang so mau cuc bo cu
// (SnapGold / SubtleGray / PillOlive) da bo 2026-08-05: hardcode o day thi doi
// skin se sot dung man nay.

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
                    CircularProgressIndicator(color = SkinTheme.colors.accentGold)
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
                        color = SkinTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    TextButton(onClick = { viewModel.load() }) {
                        Text(text = "Retry", color = SkinTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            else -> QuestContent(
                uiState = uiState,
                onGachaClick = { navController.navigate(Screen.Gacha.route) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun QuestContent(
    uiState: QuestUiState,
    onGachaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Banner gacha o TREN CUNG — day la loi vao duy nhat cua man Gacha
        item { GachaBanner(onClick = onGachaClick) }

        item { StreakBanner(streak = uiState.personalStreak) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's quests",
                    color = SkinTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${uiState.completedCount}/${uiState.quests.size}",
                    color = SkinTheme.colors.accentGold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        items(uiState.quests.size) { index ->
            QuestCard(quest = uiState.quests[index])
        }

        // Banner Astrite vua duoc thuong hom nay (xong 2/2 quest)
        uiState.rewardAstrite?.let { amount ->
            item { RewardBanner(astrite = amount) }
        }

        // Luoi "Frame collection" da CHUYEN sang man Appearance tab Frames
        // (2026-08-05 — GACHA_PLAN.md muc 6.1): trang nay giu dung viec cua no
        // la quest + streak, bo suu tap xem o mot cho duy nhat.
    }
}

/**
 * Banner mo man Gacha — anh `R.drawable.gacha_banner` hardcode trong APK
 * (user chot 2026-08-05): thay anh = ghi de file trong `res/drawable-nodpi/`.
 * Anh goc 1080×608 (~16:9) nen banner giu dung ti le do thay vi cao co dinh.
 */
@Composable
private fun GachaBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1080f / 608f)
            .clip(SkinTheme.shapes.image)
            .clickable { onClick() },
    ) {
        Image(
            painter = painterResource(R.drawable.gacha_banner),
            contentDescription = "Open the gacha",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Dai mo chan de chu doc duoc du banner sang; chu trang co y (nam tren anh)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Gacha",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Roll for skins, effects and frames",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Banner streak ca nhan — vang gamification. */
@Composable
private fun StreakBanner(streak: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.image)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, SkinTheme.colors.accentGold.copy(alpha = 0.4f), SkinTheme.shapes.image)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🔥", fontSize = 34.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "$streak",
                color = SkinTheme.colors.accentGold,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (streak == 1) "day streak" else "days streak",
                color = SkinTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Reach 3 · 7 · 14 · 30\nto unlock frames",
            color = SkinTheme.colors.textSecondary,
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
            .clip(SkinTheme.shapes.image)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SkinTheme.colors.pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // Icon theo loai quest — AI_CHALLENGE (quest thu 3 do AI sinh, 2026-08-15) = 🎯
                text = when (quest.type) {
                    "POST_MOMENT" -> "📸"
                    "AI_CHALLENGE" -> "🎯"
                    else -> "👋"
                },
                fontSize = 22.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quest.content,
                color = SkinTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (quest.completed) "Done today" else "Not done yet",
                color = if (quest.completed) SkinTheme.colors.accentGold else SkinTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(
            imageVector = if (quest.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (quest.completed) "Completed" else "Not completed",
            tint = if (quest.completed) SkinTheme.colors.accentGold else SkinTheme.colors.textSecondary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Banner "vua mo khoa khung moi" khi xong 2/2 quest hom nay. */
@Composable
private fun RewardBanner(astrite: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.image)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, SkinTheme.colors.accentGold, SkinTheme.shapes.image)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🎁", fontSize = 30.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Daily quests complete!",
                color = SkinTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "+$astrite Astrite",
                color = SkinTheme.colors.accentGold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// FrameItem da CHUYEN sang man Appearance tab Frames (2026-08-05) — dung chung
// component `CollectibleItem` voi tab Skins/Effects thay vi ve rieng o day.
