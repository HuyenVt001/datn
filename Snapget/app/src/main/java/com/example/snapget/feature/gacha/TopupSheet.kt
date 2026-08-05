package com.example.snapget.feature.gacha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.TopupPackageDto
import java.text.NumberFormat
import java.util.Locale

/** Dinh dang tien Viet: 145000 -> "145.000d". */
private fun formatVnd(amount: Int): String = NumberFormat.getInstance(Locale("vi", "VN")).format(amount) + "đ"

private fun formatAstrite(amount: Int): String = NumberFormat.getInstance(Locale("vi", "VN")).format(amount)

/**
 * Popup goi nap Astrite (GACHA_PLAN.md muc 6.5).
 *
 * Bam 1 goi -> server tao don + link PayOS -> man hinh mo link bang Chrome
 * Custom Tabs. App **khong** tu tinh tien va **khong** tu cong Astrite: bam nut
 * chi la xin mot link thanh toan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopupSheet(
    state: TopupUiState,
    onDismiss: () -> Unit,
    onBuy: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SkinTheme.colors.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Top up Astrite",
                color = SkinTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Pay with your banking app via PayOS.",
                color = SkinTheme.colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            when {
                state.isLoadingPackages -> Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = SkinTheme.colors.accent)
                }

                state.packages.isEmpty() -> Text(
                    text = "No top-up packages available right now.",
                    color = SkinTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.packages, key = { it.packageId }) { pkg ->
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
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Astrite is added automatically once the payment is confirmed. " +
                    "It can take a few seconds.",
                color = SkinTheme.colors.textSecondary,
                fontSize = 11.sp,
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
        Text(text = "⭐", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatAstrite(pkg.astrite),
                color = SkinTheme.colors.accentGold,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Astrite",
                color = SkinTheme.colors.textSecondary,
                fontSize = 12.sp,
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Dai bang "dang cho thanh toan" hien duoi o Astrite trong khi app poll trang
 * thai don. Cho phep bo cuoc de khong poll mai.
 */
@Composable
fun PendingPaymentBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
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
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) {
            Text(text = "Dismiss", color = SkinTheme.colors.accent, fontSize = 12.sp)
        }
    }
}

/** Popup bao da cong Astrite sau khi webhook PayOS ve. */
@Composable
fun TopupSuccessDialog(astrite: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkinTheme.colors.surface,
        title = {
            Text(
                text = "Payment confirmed",
                color = SkinTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
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
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}
