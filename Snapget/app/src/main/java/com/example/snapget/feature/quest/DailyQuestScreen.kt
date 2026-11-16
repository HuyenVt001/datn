package com.example.snapget.feature.quest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.snapget.R
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.button.ChunkyButton
import com.example.snapget.core.designsystem.component.lottie.SnapAnim
import com.example.snapget.core.designsystem.component.lottie.SnapLottie
import com.example.snapget.core.designsystem.component.state.ErrorState
import com.example.snapget.core.designsystem.component.state.SkeletonCards
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.feedback.rememberFeedback
import com.example.snapget.core.designsystem.motion.popOnChange
import com.example.snapget.core.designsystem.motion.staggerEnter
import com.example.snapget.core.designsystem.skin.SkinParticleBurst
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.skin.glassColor
import com.example.snapget.core.designsystem.skin.skinGlass
import com.example.snapget.core.designsystem.theme.NumberDisplay
import com.example.snapget.core.network.dto.TodayQuestDto
import java.util.Calendar

/** Moc streak mo khoa khung (khop server: 3/7/14/30 — CLAUDE.md muc 9). */
private val STREAK_MILESTONES = listOf(3, 7, 14, 30)

/** Thuong rieng quest AI (server cong +30, khong tinh vao ruong 2/2). */
private const val AI_QUEST_REWARD = 30

/** Thuong ruong khi xong 2/2 quest co dinh — hien TRUOC khi lam (server tra that sau). */
private const val CHEST_REWARD_PREVIEW = 60

/**
 * Man Daily Quest — THIET KE LAI "Hero streak + ruong" (user chot 2026-11-15,
 * UI_UPGRADE_PLAN.md): hero lua Lottie + so streak + dai 7 ngay · the quest co
 * thanh tien do, chip thuong, nut GO · ruong ngay mo bang hieu ung · duong moc
 * 3·7·14·30. Khong can API moi: suy tu `personalStreak` + `completed`.
 */
@Composable
fun DailyQuestScreen(
    viewModel: QuestViewModel = hiltViewModel(),
    // Nut GO cua quest dang bai / AI -> mo Camera
    onOpenCamera: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val chestOpened by viewModel.chestOpenedToday.collectAsState()

    // Vao man = goi /quests/today -> server tu hoan thanh quest LOGIN
    LaunchedEffect(Unit) { viewModel.load() }

    DailyQuestScreenContent(
        uiState = uiState,
        onBackClick = null,
        onRetry = { viewModel.load() },
        onGoQuest = onOpenCamera,
        chestOpened = chestOpened,
        onOpenChest = viewModel::markChestOpened,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestScreenContent(
    uiState: QuestUiState,
    onBackClick: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    onGoQuest: () -> Unit = {},
    chestOpened: Boolean = false,
    onOpenChest: () -> Unit = {},
) {
    Scaffold(
        topBar = { SimpleTopBar(title = "Daily Quest", onBackClick = onBackClick) },
        containerColor = Color.Transparent,
    ) { padding ->
        when {
            uiState.status is LoadStatus.Error && uiState.quests.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(message = uiState.status.description, onRetry = onRetry)
            }

            uiState.quests.isEmpty() &&
                (uiState.status is LoadStatus.Init || uiState.status is LoadStatus.Loading) -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                SkeletonCards() // P5: hero + 3 the quest
            }

            else -> PullToRefreshBox(
                isRefreshing = uiState.status is LoadStatus.Loading,
                onRefresh = onRetry,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                QuestContent(
                    uiState = uiState,
                    onGoQuest = onGoQuest,
                    chestOpened = chestOpened,
                    onOpenChest = onOpenChest,
                )
            }
        }
    }
}

@Composable
internal fun QuestContent(
    uiState: QuestUiState,
    modifier: Modifier = Modifier,
    onGoQuest: () -> Unit = {},
    chestOpened: Boolean = false,
    onOpenChest: () -> Unit = {},
) {
    val postedToday = uiState.quests.any { it.type == "POST_MOMENT" && it.completed }
    val fixedQuests = uiState.quests.filter { it.type != "AI_CHALLENGE" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            StreakHero(streak = uiState.personalStreak, postedToday = postedToday)
        }

        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's quests",
                    color = SkinTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${uiState.completedCount} / ${uiState.quests.size}",
                    color = SkinTheme.colors.accentGold,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.popOnChange(uiState.completedCount),
                )
            }
        }

        itemsIndexed(uiState.quests, key = { _, q -> q.questId }) { index, quest ->
            QuestCard(
                quest = quest,
                onGo = onGoQuest,
                modifier = Modifier.staggerEnter(index),
            )
        }

        item(key = "chest") {
            DailyChest(
                done = fixedQuests.count { it.completed },
                total = fixedQuests.size.coerceAtLeast(2),
                reward = uiState.rewardAstrite ?: CHEST_REWARD_PREVIEW,
                unlocked = uiState.rewardAstrite != null,
                opened = chestOpened,
                onOpen = onOpenChest,
            )
        }

        item(key = "milestones") {
            MilestonePath(streak = uiState.personalStreak)
        }
    }
}

// ======================= HERO STREAK =======================

/** Lua Lottie + so streak lon + dai 7 ngay trong tuan. */
@Composable
private fun StreakHero(streak: Int, postedToday: Boolean) {
    val colors = SkinTheme.colors
    val shown by animateIntAsState(streak, tween(800), label = "streakCount")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.card)
            .skinGlass(SkinTheme.shapes.card)
            .border(1.dp, colors.streak.copy(alpha = 0.35f), SkinTheme.shapes.card)
            .padding(vertical = 18.dp, horizontal = 16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        SnapLottie(anim = SnapAnim.STREAK_FIRE, loop = true, modifier = Modifier.size(96.dp))
        Text(
            text = "$shown",
            style = NumberDisplay,
            color = if (streak > 0) colors.streak else colors.textSecondary,
        )
        Text(
            text = "day streak",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                postedToday -> "You're on fire today! 🔥"
                streak > 0 -> "Post a moment today to keep it going"
                else -> "Post a moment to start your streak"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (postedToday) colors.success else colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        WeekStrip(streak = streak, postedToday = postedToday)
    }
}

/**
 * 7 cham T2 -> CN cua tuan nay. Cham day = ngay da dang (suy tu streak lien tiep:
 * neu hom nay da dang thi streak tinh tu hom nay lui ve, chua thi tu hom qua).
 */
@Composable
private fun WeekStrip(streak: Int, postedToday: Boolean) {
    val colors = SkinTheme.colors
    // Calendar (khong java.time) vi minSdk 24
    val todayIndex = remember {
        // MONDAY=2 ... SUNDAY=1 -> 0..6 tinh tu thu Hai
        (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { i, label ->
            val daysAgo = todayIndex - i
            val first = if (postedToday) 0 else 1
            val last = if (postedToday) streak - 1 else streak
            val filled = daysAgo in first..last && daysAgo >= 0
            val isToday = daysAgo == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) colors.textPrimary else colors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (filled) colors.streak else colors.surfaceVariant)
                        .then(
                            if (isToday) Modifier.border(2.dp, colors.textPrimary, CircleShape) else Modifier,
                        ),
                ) {
                    if (filled) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

// ======================= QUEST CARD =======================

/**
 * The 1 quest: icon tron + noi dung + thanh tien do 0/1 + chip thuong + nut GO.
 * Xong -> vien `success`, check "pop" + rung (khi trang thai doi luc dang xem).
 */
@Composable
private fun QuestCard(
    quest: TodayQuestDto,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkinTheme.colors
    val feedback = rememberFeedback()
    var wasCompleted by remember(quest.questId) { mutableStateOf(quest.completed) }
    LaunchedEffect(quest.completed) {
        if (quest.completed && !wasCompleted) feedback.success()
        wasCompleted = quest.completed
    }
    val progress by animateFloatAsState(
        targetValue = if (quest.completed) 1f else 0f,
        animationSpec = tween(600),
        label = "questProgress",
    )
    val isAi = quest.type == "AI_CHALLENGE"
    val canGo = !quest.completed && (quest.type == "POST_MOMENT" || isAi)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.card)
            .background(glassColor())
            .border(
                width = 2.dp,
                color = if (quest.completed) colors.success.copy(alpha = 0.6f) else colors.surfaceVariant,
                shape = SkinTheme.shapes.card,
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (quest.completed) colors.success else colors.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        quest.completed -> Icons.Outlined.Check
                        quest.type == "POST_MOMENT" -> Icons.Outlined.PhotoCamera
                        isAi -> Icons.Outlined.CenterFocusStrong
                        else -> Icons.Outlined.WavingHand
                    },
                    contentDescription = null,
                    tint = if (quest.completed) colors.onAccent else colors.textPrimary,
                    modifier = Modifier.size(24.dp).popOnChange(quest.completed),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quest.content,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                RewardChip(isAi = isAi)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (canGo) {
                ChunkyButton(
                    text = "Go",
                    onClick = onGo,
                    height = 40.dp,
                    depth = 3.dp,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Thanh tien do 0/1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .semantics { contentDescription = if (quest.completed) "Completed" else "Not completed" },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(colors.success),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (quest.completed) "1/1" else "0/1",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (quest.completed) colors.success else colors.textSecondary,
            )
        }
    }
}

/** Chip thuong: quest AI = 💎 +30 rieng; quest co dinh = gop vao ruong ngay. */
@Composable
private fun RewardChip(isAi: Boolean) {
    val colors = SkinTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isAi) {
            Image(
                painter = painterResource(R.drawable.ic_astrite),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "+$AI_QUEST_REWARD Astrite",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentGold,
            )
        } else {
            Text(
                text = "🎁 Counts toward today's chest",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

// ======================= DAILY CHEST =======================

/**
 * Ruong ngay: khoa cho toi khi xong 2/2 quest co dinh; mo khoa -> nut "Open" ->
 * Lottie mo qua + so Astrite dem len + rung. Thuong server DA cong khi xong
 * 2/2 — mo ruong chi la hieu ung, nho "da mo hom nay" (QuestViewModel).
 */
@Composable
private fun DailyChest(
    done: Int,
    total: Int,
    reward: Int,
    unlocked: Boolean,
    opened: Boolean,
    onOpen: () -> Unit,
) {
    val colors = SkinTheme.colors
    val feedback = rememberFeedback()
    // Dem so lan bam mo trong phien de chay lai animation (key)
    var openTick by remember { mutableIntStateOf(0) }
    val shownReward by animateIntAsState(
        targetValue = if (opened) reward else 0,
        animationSpec = tween(900),
        label = "chestReward",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.card)
            .background(glassColor())
            .border(
                width = 2.dp,
                color = if (unlocked) colors.accentGold else colors.surfaceVariant,
                shape = SkinTheme.shapes.card,
            )
            .padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
            if (openTick > 0) {
                key(openTick) {
                    SnapLottie(anim = SnapAnim.GIFT_OPEN, modifier = Modifier.size(110.dp))
                }
                // Mo ruong: hat cua skin bung ra quanh ruong (vung ve rong hon o 110dp)
                SkinParticleBurst(
                    trigger = openTick,
                    count = 18,
                    modifier = Modifier.requiredSize(240.dp),
                )
            } else {
                SnapLottie(
                    anim = SnapAnim.GIFT_OPEN,
                    isPlaying = false,
                    modifier = Modifier.size(110.dp),
                )
                if (!unlocked) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceVariant)
                            .padding(5.dp),
                    )
                }
            }
        }

        Text(
            text = when {
                opened -> "Chest collected!"
                unlocked -> "Your chest is ready!"
                else -> "Daily chest"
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_astrite),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (opened) "+$shownReward Astrite" else "+$reward Astrite",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = colors.accentGold,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            opened -> Text(
                text = "Come back tomorrow for a new chest",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            unlocked -> ChunkyButton(
                text = "Open chest",
                onClick = {
                    openTick++
                    feedback.celebrate()
                    onOpen()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text(
                text = "Complete $done / $total daily quests to unlock",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

// ======================= MILESTONE PATH =======================

/** Duong moc 3·7·14·30: cham da dat to `accent`, con lai xam; dong chu "con N ngay". */
@Composable
private fun MilestonePath(streak: Int) {
    val colors = SkinTheme.colors
    val next = STREAK_MILESTONES.firstOrNull { it > streak }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.card)
            .skinGlass(SkinTheme.shapes.card)
            .padding(16.dp),
    ) {
        Text(
            text = "Streak rewards",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (next != null) {
                "${next - streak} more day${if (next - streak == 1) "" else "s"} to unlock the $next-day frame"
            } else {
                "All streak frames unlocked — legend! 🏆"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            val reachedColor = colors.accent
            val idleColor = colors.surfaceVariant
            // Duong noi giua cac moc (ve bang Canvas — chi trang tri)
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val y = size.height / 2
                val stepX = size.width / (STREAK_MILESTONES.size - 1).coerceAtLeast(1)
                for (i in 0 until STREAK_MILESTONES.size - 1) {
                    val reached = streak >= STREAK_MILESTONES[i + 1]
                    drawLine(
                        color = if (reached) reachedColor else idleColor,
                        start = Offset(stepX * i, y),
                        end = Offset(stepX * (i + 1), y),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                STREAK_MILESTONES.forEach { m ->
                    val reached = streak >= m
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (reached) colors.accent else colors.surfaceVariant)
                                .semantics {
                                    contentDescription = "$m-day frame ${if (reached) "unlocked" else "locked"}"
                                },
                        ) {
                            Icon(
                                imageVector = if (reached) Icons.Outlined.Check else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = if (reached) colors.onAccent else colors.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "$m",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reached) colors.accent else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
