package com.example.snapget.core.designsystem.component.celebration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.button.ChunkyButton
import com.example.snapget.core.designsystem.component.lottie.SnapAnim
import com.example.snapget.core.designsystem.component.lottie.SnapLottie
import com.example.snapget.core.designsystem.feedback.rememberFeedback
import com.example.snapget.core.designsystem.motion.MotionTokens
import com.example.snapget.core.designsystem.motion.rememberReducedMotion
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.theme.NumberDisplay
import kotlinx.coroutines.delay

/** Thoi gian tu dong dong man an mung "nho" (streak +1 thuong, khong cham moc). */
private const val AUTO_DISMISS_MS = 2600L

/**
 * Man phu TOAN MAN an mung (DESIGN.md muc 0.5 — khoanh khac LON):
 * - Streak +1: lua Lottie nay + so streak DEM LEN (N-1 -> N) + rung;
 *   tu dong dong sau ~2.6s hoac cham bat ky dau.
 * - Cham moc 3/7/14/30: them confetti + cup + preview khung vua mo khoa; KHONG tu
 *   dong (nguoi dung bam "Continue") de kip nhin phan thuong.
 * - Kem dong "🎯 Challenge complete! +30 Astrite" neu quest AI khop.
 *
 * Reduced motion: Lottie dung o frame cuoi, so hien thang gia tri moi.
 */
@Composable
fun StreakCelebration(
    streak: Int?,
    milestone: Int?,
    unlockedFrameUrl: String?,
    aiChallengeComplete: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = SkinTheme.colors
    val feedback = rememberFeedback()
    val reduced = rememberReducedMotion()
    val isMilestone = milestone != null

    // So dem len: bat dau tu N-1, sau 1 nhip chuyen sang N
    var target by remember { mutableIntStateOf(((streak ?: 1) - 1).coerceAtLeast(0)) }
    val shown by animateIntAsState(target, tween(if (reduced) 0 else 700), label = "streakUp")
    // Noi dung "nay" vao (scale 0.6 -> 1)
    val enter = remember { Animatable(if (reduced) 1f else 0.6f) }

    LaunchedEffect(Unit) {
        when {
            streak != null -> feedback.celebrate()
            else -> feedback.success() // chi quest AI
        }
        if (!reduced) enter.animateTo(1f, MotionTokens.pop)
        delay(250)
        target = streak ?: 0
        if (!isMilestone) {
            delay(AUTO_DISMISS_MS)
            onDismiss()
        }
    }
    BackHandler(onBack = onDismiss)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            // Cham bat ky dau = dong (khong ripple — ca man la vung cham)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Close",
                onClick = onDismiss,
            ),
    ) {
        if (isMilestone) {
            SnapLottie(
                anim = SnapAnim.CONFETTI,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    scaleX = enter.value
                    scaleY = enter.value
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (streak != null) {
                SnapLottie(
                    anim = if (isMilestone) SnapAnim.TROPHY else SnapAnim.STREAK_FIRE,
                    loop = !isMilestone,
                    modifier = Modifier.size(170.dp),
                )
                Text(
                    text = "$shown",
                    style = NumberDisplay.copy(fontSize = NumberDisplay.fontSize * 1.6f),
                    color = colors.streak,
                )
                Text(
                    text = if (isMilestone) "$milestone day streak!" else "day streak!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isMilestone) {
                        "Incredible! You unlocked a new frame 🎉"
                    } else {
                        "You're on fire! Come back tomorrow to keep it going."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                SnapLottie(anim = SnapAnim.SUCCESS_CHECK, modifier = Modifier.size(150.dp))
            }

            if (unlockedFrameUrl != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(SkinTheme.shapes.card)
                        .background(colors.surfaceVariant),
                ) {
                    AsyncImage(
                        model = unlockedFrameUrl,
                        contentDescription = "Unlocked frame",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }

            if (aiChallengeComplete) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "🎯 Challenge complete! +30 Astrite",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accentGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(SkinTheme.shapes.pill)
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // Moc streak: nut Continue (khong tu dong dong)
        if (isMilestone) {
            ChunkyButton(
                text = "Continue",
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .fillMaxWidth(),
            )
        }
    }
}
