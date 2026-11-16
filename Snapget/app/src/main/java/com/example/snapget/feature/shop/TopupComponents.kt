package com.example.snapget.feature.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.snapget.R
import com.example.snapget.core.designsystem.skin.SkinAlertDialog
import com.example.snapget.core.designsystem.skin.SkinLoading
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.TopupPackageDto
import java.text.NumberFormat
import java.util.Locale

/** Dinh dang tien Viet: 145000 -> "145.000d". */
internal fun formatVnd(amount: Int): String = NumberFormat.getInstance(Locale("vi", "VN")).format(amount) + "đ"

internal fun formatAstrite(amount: Int): String = NumberFormat.getInstance(Locale("vi", "VN")).format(amount)

/**
 * Tab Astrite cua Shop — danh sach goi nap PayOS (chuyen tu `TopupSheet` cua
 * man Gacha cu, 2026-11-15: tu bottom sheet thanh 1 tab).
 *
 * Bam 1 goi -> server tao don + link PayOS -> man hinh mo link bang Chrome
 * Custom Tabs. App **khong** tu tinh tien va **khong** tu cong Astrite: bam nut
 * chi la xin mot link thanh toan.
 */
@Composable
internal fun AstriteTab(
    state: TopupUiState,
    onBuy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "Top up Astrite",
                    color = SkinTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Pay with your banking app via PayOS.",
                    color = SkinTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        when {
            state.isLoadingPackages && state.packages.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SkinLoading(fallbackColor = SkinTheme.colors.accent)
                }
            }

            state.packages.isEmpty() -> item {
                Text(
                    text = "No top-up packages available right now.",
                    color = SkinTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }

            else -> items(state.packages, key = { it.packageId }) { pkg ->
                PackageRow(
                    pkg = pkg,
                    // Dang tao don cho goi nao thi chi khoa dung goi do
                    isBusy = state.creatingPackageId == pkg.packageId,
                    // ...nhung dang tao don thi khong cho bam goi khac
                    enabled = state.creatingPackageId == null,
                    onClick = { onBuy(pkg.packageId) },
                )
            }
        }

        item {
            Text(
                text = "Astrite is added automatically once the payment is confirmed. " +
                    "It can take a few seconds.\nFree Astrite: finish your daily quests.",
                color = SkinTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PackageRow(
    pkg: TopupPackageDto,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.card)
            .background(SkinTheme.colors.surfaceVariant)
            .clickable(enabled = enabled && !isBusy, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_astrite),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatAstrite(pkg.astrite),
                color = SkinTheme.colors.accentGold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pkg.name.ifBlank { "Astrite" },
                color = SkinTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = SkinTheme.colors.accent,
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(SkinTheme.shapes.pill)
                    .background(SkinTheme.colors.accent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = formatVnd(pkg.priceVnd),
                    color = SkinTheme.colors.onAccent,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/**
 * Dai bang "dang cho thanh toan" hien tren dau noi dung Shop trong khi app poll
 * trang thai don. Cho phep bo cuoc de khong poll mai.
 */
@Composable
internal fun PendingPaymentBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SkinTheme.shapes.card)
            .background(SkinTheme.colors.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = SkinTheme.colors.accent,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Waiting for payment confirmation...",
            color = SkinTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) {
            Text(text = "Dismiss", color = SkinTheme.colors.accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Popup bao da cong Astrite sau khi webhook PayOS ve. */
@Composable
internal fun TopupSuccessDialog(astrite: Int, onDismiss: () -> Unit) {
    SkinAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkinTheme.colors.surface,
        title = {
            Text(
                text = "Payment confirmed",
                color = SkinTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = "+${formatAstrite(astrite)} Astrite has been added to your balance.",
                color = SkinTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Nice",
                    color = SkinTheme.colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}
