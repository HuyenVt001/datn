package com.example.snapget.feature.gacha

import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.GachaStateDto
import kotlinx.coroutines.delay

/** Khoang cach giua 2 lan hoi trang thai don nap. */
private const val TOPUP_POLL_INTERVAL_MS = 3_000L

/**
 * So lan hoi toi da moi luot foreground (~3 phut). Het so lan thi ngung —
 * `getOrder` khong tu chuyen don sang EXPIRED nen khong ngung se poll mai. Vao
 * lai man hinh la so du duoc doc lai tu server, khong mat gi.
 */
private const val TOPUP_POLL_MAX_ATTEMPTS = 60

/**
 * Man Gacha (GACHA_PLAN.md muc 6.2).
 *
 * Day la man **DUY NHAT** hien so du Astrite (user chot 2026-08-05) — feed va
 * trang Daily giu sach, khong hien tien.
 *
 * Nut `+` mo popup goi nap (G6 — PayOS, TIEN THAT).
 */
@Composable
fun GachaScreen(
    navController: NavController,
    viewModel: GachaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showRules by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    // Loi quay hien toast roi thoi — KHONG thay the ca man bang man loi, vi so du
    // va pity van dung, nguoi dung chi can bam lai
    LaunchedEffect(uiState.rollError) {
        uiState.rollError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissRollError()
        }
    }

    LaunchedEffect(uiState.topup.message) {
        uiState.topup.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissTopupMessage()
        }
    }

    // Vua tao don xong -> mo trang thanh toan PayOS bang Chrome Custom Tabs
    LaunchedEffect(uiState.topup.checkoutUrl) {
        uiState.topup.checkoutUrl?.let { url ->
            runCatching {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            }.onFailure {
                Toast.makeText(context, "No browser to open the payment page.", Toast.LENGTH_LONG)
                    .show()
            }
            viewModel.consumeCheckoutUrl()
        }
    }

    // Hoi trang thai don trong luc cho webhook PayOS ve.
    // repeatOnLifecycle(STARTED): luc nguoi dung dang o trang PayOS thi app o
    // background -> ngung hoi; quay lai app la hoi ngay, dung luc can nhat.
    val pendingOrderCode = uiState.topup.pendingOrderCode
    LaunchedEffect(pendingOrderCode) {
        val orderCode = pendingOrderCode ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var attempts = 0
            while (
                viewModel.uiState.value.topup.pendingOrderCode == orderCode &&
                attempts < TOPUP_POLL_MAX_ATTEMPTS
            ) {
                viewModel.refreshPendingOrder()
                attempts++
                delay(TOPUP_POLL_INTERVAL_MS)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SkinTheme.colors.background)) {
        when {
            uiState.status is LoadStatus.Loading && uiState.state.costSingle == 0 -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SkinTheme.colors.accent)
                }
            }

            uiState.status is LoadStatus.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.status.description,
                        color = SkinTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { viewModel.load() }) {
                        Text(
                            text = "Retry",
                            color = SkinTheme.colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            else -> GachaContent(
                state = uiState.state,
                isRolling = uiState.isRolling,
                isAwaitingPayment = pendingOrderCode != null,
                onBack = { navController.popBackStack() },
                onShowRules = { showRules = true },
                onRoll = viewModel::roll,
                onTopup = viewModel::openTopup,
                onCancelPayment = viewModel::cancelPendingOrder,
            )
        }

        uiState.outcome?.let { outcome ->
            GachaResultOverlay(outcome = outcome, onDismiss = viewModel::dismissOutcome)
        }
    }

    if (showRules) {
        GachaRulesDialog(state = uiState.state, onDismiss = { showRules = false })
    }

    if (uiState.topup.isSheetOpen) {
        TopupSheet(
            state = uiState.topup,
            onDismiss = viewModel::closeTopup,
            onBuy = viewModel::buyPackage,
        )
    }

    uiState.topup.creditedAstrite?.let { astrite ->
        TopupSuccessDialog(astrite = astrite, onDismiss = viewModel::dismissCreditedAstrite)
    }
}

@Composable
private fun GachaContent(
    state: GachaStateDto,
    isRolling: Boolean,
    isAwaitingPayment: Boolean,
    onBack: () -> Unit,
    onShowRules: () -> Unit,
    onRoll: (Int) -> Unit,
    onTopup: () -> Unit,
    onCancelPayment: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SkinTheme.colors.textPrimary,
                )
            }

            // O Astrite + nut `+` mo popup nap. Ca cum bam duoc, khong chi rieng
            // dau `+` — vung cham 24dp qua nho de bam trung.
            Row(
                modifier = Modifier
                    .clip(SkinTheme.shapes.pill)
                    .background(SkinTheme.colors.pill)
                    .clickable(onClick = onTopup)
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "⭐", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "%,d".format(state.astrite),
                    color = SkinTheme.colors.accentGold,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Top up Astrite",
                    tint = SkinTheme.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onShowRules) {
                Icon(
                    imageVector = Icons.Filled.HelpOutline,
                    contentDescription = "Gacha rules",
                    tint = SkinTheme.colors.textPrimary,
                )
            }
        }

        // CHI hien pity SSR (user chot 2026-08-05) — pity R/SR an hoan toan
        Text(
            text = "SSR ${state.pity.SSR}/${state.pityLimit.SSR}",
            color = SkinTheme.colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )

        if (isAwaitingPayment) {
            PendingPaymentBanner(
                onCancel = onCancelPayment,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "Snapget Gacha",
            color = SkinTheme.colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Skins · touch effects · frames",
            color = SkinTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RollButton(
                label = "Roll x1",
                cost = state.costSingle,
                enabled = !isRolling && state.astrite >= state.costSingle,
                loading = isRolling,
                modifier = Modifier.weight(1f),
                onClick = { onRoll(1) },
            )
            RollButton(
                label = "Roll x${state.tenTimes}",
                cost = state.costTen,
                enabled = !isRolling && state.astrite >= state.costTen,
                loading = isRolling,
                modifier = Modifier.weight(1f),
                onClick = { onRoll(state.tenTimes) },
            )
        }

        if (state.astrite < state.costSingle) {
            Text(
                text = "Not enough Astrite — finish your daily quests, or tap ⭐ to top up.",
                color = SkinTheme.colors.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun RollButton(
    label: String,
    cost: Int,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = SkinTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = SkinTheme.colors.accent,
            contentColor = SkinTheme.colors.onAccent,
            disabledContainerColor = SkinTheme.colors.surfaceVariant,
            disabledContentColor = SkinTheme.colors.textSecondary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = SkinTheme.colors.onAccent,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = label, fontWeight = FontWeight.Bold)
                Text(text = "⭐ $cost", fontSize = 11.sp)
            }
        }
    }
}

/**
 * Popup Rule gacha (GACHA_PLAN.md muc 6.4).
 *
 * Hien **ti le GOC** (đúng chuẩn công bố), va sinh tu chinh `GET /gacha/state`
 * nen so hien ra luon khop so dang chay o server — sua ti le o server la popup
 * tu dung theo, khong phai sua app.
 */
@Composable
private fun GachaRulesDialog(state: GachaStateDto, onDismiss: () -> Unit) {
    fun pct(value: Double) = "%.1f%%".format(value * 100)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkinTheme.colors.surface,
        title = {
            Text(
                text = "Gacha rules",
                color = SkinTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Drop rates",
                    color = SkinTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
                RuleRow("SSR — Skins", pct(state.rates.SSR), GachaRarity.SSR)
                RuleRow("SR — Touch effects", pct(state.rates.SR), GachaRarity.SR)
                RuleRow("R — Frames", pct(state.rates.R), GachaRarity.R)
                RuleRow("N — Astrite", pct(state.rates.N), GachaRarity.N)

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Pity",
                    color = SkinTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
                RuleRow("SSR guaranteed within", "${state.pityLimit.SSR} rolls", GachaRarity.SSR)
                RuleRow("SR guaranteed within", "${state.pityLimit.SR} rolls", GachaRarity.SR)
                RuleRow("R guaranteed within", "${state.pityLimit.R} rolls", GachaRarity.R)

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Duplicates",
                    color = SkinTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
                RuleRow("Duplicate SSR", "+${state.refunds.SSR} ⭐", GachaRarity.SSR)
                RuleRow("Duplicate SR", "+${state.refunds.SR} ⭐", GachaRarity.SR)
                RuleRow("Duplicate R", "+${state.refunds.R} ⭐", GachaRarity.R)

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "SR and SSR always give you something you don't own yet — " +
                        "until you've collected them all.",
                    color = SkinTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = SkinTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

/** Vien mo trang tri — giu de sau nay cam anh nen gacha that (P5). */
@Composable
internal fun GachaBackdropPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(SkinTheme.shapes.card)
            .background(SkinTheme.colors.surface)
            .border(1.dp, SkinTheme.colors.accent.copy(alpha = 0.4f), SkinTheme.shapes.card),
    )
}
