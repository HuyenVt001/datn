package com.example.snapget.feature.shop

import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.example.snapget.R
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.collectible.CollectibleItem
import com.example.snapget.core.designsystem.component.collectible.CollectibleState
import com.example.snapget.core.designsystem.component.collectible.EffectDemoCell
import com.example.snapget.core.designsystem.component.collectible.SkinThumbnail
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.skin.SkinRegistry
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.ShopItemDto
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
 * Man Shop (SHOP_PLAN.md muc P3) — tab footer, **khong nut Back**.
 *
 * 4 tab Astrite · Skins · Frames · Effects. Mua bang Astrite; nap Astrite qua
 * PayOS (TIEN THAT) o tab Astrite. Day la man DUY NHAT hien so du Astrite.
 */
@Composable
fun ShopScreen(viewModel: ShopViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Moi lan vao tab la doc lai so du (co the vua doi do quest/nap o noi khac)
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }

    // Vua tao don xong -> mo trang thanh toan PayOS bang Chrome Custom Tabs
    LaunchedEffect(uiState.topup.checkoutUrl) {
        uiState.topup.checkoutUrl?.let { url ->
            runCatching {
                CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
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

    ShopScreenContent(
        uiState = uiState,
        onTabSelected = viewModel::selectTab,
        onRetry = viewModel::load,
        onBuyItem = viewModel::requestBuy,
        onApplyItem = viewModel::apply,
        onBuyPackage = viewModel::buyPackage,
        onCancelPayment = viewModel::cancelPendingOrder,
    )

    uiState.confirmItem?.let { item ->
        BuyConfirmDialog(
            item = item,
            astrite = uiState.astrite,
            onConfirm = viewModel::confirmBuy,
            onTopup = viewModel::goToTopup,
            onDismiss = viewModel::dismissConfirm,
        )
    }

    uiState.applyPrompt?.let { item ->
        ApplyNowDialog(
            item = item,
            onApply = viewModel::applyPrompted,
            onDismiss = viewModel::dismissApplyPrompt,
        )
    }

    uiState.topup.creditedAstrite?.let { astrite ->
        TopupSuccessDialog(astrite = astrite, onDismiss = viewModel::dismissCreditedAstrite)
    }
}

/** Phan stateless cua man Shop — dung duoc trong @Preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreenContent(
    uiState: ShopUiState,
    onTabSelected: (ShopTab) -> Unit = {},
    onRetry: () -> Unit = {},
    onBuyItem: (ShopItemDto) -> Unit = {},
    onApplyItem: (ShopItemDto) -> Unit = {},
    onBuyPackage: (String) -> Unit = {},
    onCancelPayment: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shop",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                },
                actions = {
                    AstriteBalance(
                        astrite = uiState.astrite,
                        onClick = { onTabSelected(ShopTab.ASTRITE) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = SkinTheme.colors.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val selectedIndex = uiState.tab.ordinal
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = SkinTheme.colors.background,
                contentColor = SkinTheme.colors.accent,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                        height = 3.dp,
                        color = SkinTheme.colors.accent,
                    )
                },
            ) {
                ShopTab.entries.forEach { tab ->
                    val selected = tab == uiState.tab
                    Tab(
                        selected = selected,
                        onClick = { onTabSelected(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    SkinTheme.colors.accent
                                } else {
                                    SkinTheme.colors.textSecondary
                                },
                            )
                        },
                    )
                }
            }

            if (uiState.topup.pendingOrderCode != null) {
                PendingPaymentBanner(
                    onCancel = onCancelPayment,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }

            when {
                // Tab Astrite khong phu thuoc catalog — van nap duoc khi catalog loi
                uiState.tab == ShopTab.ASTRITE -> AstriteTab(
                    state = uiState.topup,
                    onBuy = onBuyPackage,
                )

                uiState.status is LoadStatus.Loading && uiState.items.isEmpty() -> {
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
                        TextButton(onClick = onRetry) {
                            Text(
                                text = "Retry",
                                color = SkinTheme.colors.accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                else -> ItemsGrid(
                    uiState = uiState,
                    items = uiState.itemsOf(uiState.tab),
                    onBuy = onBuyItem,
                    onApply = onApplyItem,
                )
            }
        }
    }
}

/** O so du `⭐ N +` tren top bar — bam de sang tab Astrite nap them. */
@Composable
private fun AstriteBalance(astrite: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(SkinTheme.shapes.pill)
            .background(SkinTheme.colors.pill)
            .clickable(onClick = onClick)
            // Cao >= 40dp de vung cham du lon (thanh co chu 14sp)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_astrite),
            contentDescription = "Astrite",
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatAstrite(astrite),
            color = SkinTheme.colors.accentGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.AddCircle,
            contentDescription = "Top up Astrite",
            tint = SkinTheme.colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Luoi 2 cot cho tab Skins/Frames/Effects. Ti le o theo loai giong man
 * Appearance: skin 9:16, khung va hieu ung 1:1.
 */
@Composable
private fun ItemsGrid(
    uiState: ShopUiState,
    items: List<ShopItemDto>,
    onBuy: (ShopItemDto) -> Unit,
    onApply: (ShopItemDto) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Nothing on sale here yet — check back soon!",
                color = SkinTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.itemId }) { item ->
            ShopItemCard(
                item = item,
                inUse = uiState.isInUse(item),
                canAfford = uiState.canAfford(item),
                isBuying = uiState.buyingItemId == item.itemId,
                onBuy = { onBuy(item) },
                onApply = { onApply(item) },
            )
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItemDto,
    inUse: Boolean,
    canAfford: Boolean,
    isBuying: Boolean,
    onBuy: () -> Unit,
    onApply: () -> Unit,
) {
    // Bam o hieu ung -> chay lai demo (cham thu truoc khi mua)
    var replay by remember(item.itemId) { mutableIntStateOf(0) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CollectibleItem(
            name = item.itemName,
            // Hang dang ban KHONG dung LOCKED (lam mo + 🔒) — o Shop moi thu chua
            // mua deu "chua so huu", lam mo het thi ca ke hang xam xit.
            state = if (inUse) CollectibleState.IN_USE else CollectibleState.OWNED,
            aspectRatio = if (item.itemType == ShopItemDto.TYPE_SKIN) 9f / 16f else 1f,
            onClick = {
                when {
                    item.itemType == ShopItemDto.TYPE_EFFECT -> replay++
                    !item.isOwned -> onBuy()
                }
            },
        ) {
            ItemPreview(item = item, replay = replay)
        }

        Spacer(Modifier.height(6.dp))

        PriceButton(
            item = item,
            inUse = inUse,
            canAfford = canAfford,
            isBuying = isBuying,
            onBuy = onBuy,
            onApply = onApply,
        )
    }
}

/**
 * Hinh cua 1 mat hang: co `imageUrl` (admin tai len) thi dung anh do; khong co
 * thi skin/effect tu ve tu asset trong APK (item da duoc loc chi con id APK
 * nay biet — `ShopViewModel.supportedItems`).
 */
@Composable
private fun ItemPreview(item: ShopItemDto, replay: Int) {
    val id = item.refId.toIntOrNull()
    val skin = if (item.itemType == ShopItemDto.TYPE_SKIN) SkinRegistry.all.firstOrNull { it.id == id } else null
    val effect = if (item.itemType == ShopItemDto.TYPE_EFFECT) {
        TouchEffectRegistry.all.firstOrNull { it.id == id }
    } else {
        null
    }

    when {
        item.imageUrl != null -> AsyncImage(
            model = item.imageUrl,
            contentDescription = item.itemName,
            contentScale = if (item.itemType == ShopItemDto.TYPE_SKIN) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(if (item.itemType == ShopItemDto.TYPE_FRAME) 6.dp else 0.dp),
        )

        skin != null -> SkinThumbnail(skin)

        effect != null -> EffectDemoCell(effect = effect, replay = replay)
    }
}

/**
 * Nut duoi moi o: chua mua -> `⭐ gia`; da mua -> "Apply" / "In use" (skin,
 * effect) hoac "Owned" (khung — chon khung nam o luong dang bai).
 */
@Composable
private fun PriceButton(
    item: ShopItemDto,
    inUse: Boolean,
    canAfford: Boolean,
    isBuying: Boolean,
    onBuy: () -> Unit,
    onApply: () -> Unit,
) {
    val base = Modifier.fillMaxWidth().height(36.dp).clip(SkinTheme.shapes.pill)

    when {
        isBuying -> Box(
            modifier = base.background(SkinTheme.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = SkinTheme.colors.onAccent,
            )
        }

        !item.isOwned -> Row(
            modifier = base
                .background(SkinTheme.colors.accent)
                // Thieu tien VAN bam duoc: dialog se goi y sang tab Astrite
                .alpha(if (canAfford) 1f else 0.6f)
                .clickable(onClick = onBuy),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_astrite),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatAstrite(item.price),
                color = SkinTheme.colors.onAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        item.itemType == ShopItemDto.TYPE_FRAME || inUse -> Box(
            modifier = base.background(SkinTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (inUse) "In use" else "Owned",
                color = SkinTheme.colors.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }

        else -> Box(
            modifier = base.background(SkinTheme.colors.pill).clickable(onClick = onApply),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Apply",
                color = SkinTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

/** Dialog xac nhan mua — thieu Astrite thi doi thanh goi y nap. */
@Composable
private fun BuyConfirmDialog(
    item: ShopItemDto,
    astrite: Int,
    onConfirm: () -> Unit,
    onTopup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val affordable = astrite >= item.price
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkinTheme.colors.surface,
        title = {
            Text(
                text = if (affordable) "Buy ${item.itemName}?" else "Not enough Astrite",
                color = SkinTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = if (affordable) {
                    "This costs ${formatAstrite(item.price)} Astrite. " +
                        "You'll have ${formatAstrite(astrite - item.price)} left."
                } else {
                    "${item.itemName} costs ${formatAstrite(item.price)} Astrite — you need " +
                        "${formatAstrite(item.price - astrite)} more. Top up, or finish your daily quests."
                },
                color = SkinTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = if (affordable) onConfirm else onTopup) {
                Text(
                    text = if (affordable) "Buy" else "Top up",
                    color = SkinTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = SkinTheme.colors.textSecondary)
            }
        },
    )
}

/** Mua xong skin/effect -> hoi ap dung luon (user chot 2026-11-15). */
@Composable
private fun ApplyNowDialog(item: ShopItemDto, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkinTheme.colors.surface,
        title = {
            Text(
                text = "Apply now?",
                color = SkinTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "${item.itemName} is yours! Use it right away? " +
                    "You can change it anytime in Appearance.",
                color = SkinTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(text = "Apply", color = SkinTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Later", color = SkinTheme.colors.textSecondary)
            }
        },
    )
}
