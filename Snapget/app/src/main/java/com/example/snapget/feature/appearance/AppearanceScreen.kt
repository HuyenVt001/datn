package com.example.snapget.feature.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.collectible.CollectibleItem
import com.example.snapget.core.designsystem.component.collectible.CollectibleState
import com.example.snapget.core.designsystem.component.collectible.EffectDemoCell
import com.example.snapget.core.designsystem.component.collectible.SkinThumbnail
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.navigation.Screen

private val TABS = listOf("Frames", "Skins", "Effects")

/**
 * Man Appearance (SKIN_PLAN.md muc 4) — noi doi giao dien va hieu ung cham.
 *
 * Day cung la cho THAY THE muc "Theme" cu trong Settings: giao dien Light da go
 * han, o dau tien cua tab Skins chinh la giao dien den mac dinh.
 */
@Composable
fun AppearanceScreen(
    navController: NavController,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    AppearanceScreenContent(
        uiState = uiState,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onBackClick = { navController.popBackStack() },
        onApplySkin = viewModel::applySkin,
        onApplyEffect = viewModel::applyEffect,
        onRetry = { viewModel.load() },
        onOpenShop = {
            // Shop la 1 tab footer -> dieu huong y nhu bam tab (giu state cac tab)
            navController.navigate(Screen.Shop.route) {
                popUpTo(Screen.Camera.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}

@Composable
fun AppearanceScreenContent(
    uiState: AppearanceUiState,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    onBackClick: () -> Unit = {},
    onApplySkin: (Int) -> Unit = {},
    onApplyEffect: (Int) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenShop: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            SimpleTopBar(title = "Appearance", onBackClick = onBackClick)
        },
        containerColor = SkinTheme.colors.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SkinTheme.colors.background,
                contentColor = SkinTheme.colors.accent,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                        height = 3.dp,
                        color = SkinTheme.colors.accent,
                    )
                },
            ) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                color = if (selectedTab == index) {
                                    SkinTheme.colors.accent
                                } else {
                                    SkinTheme.colors.textSecondary
                                },
                            )
                        },
                    )
                }
            }

            when {
                uiState.status is LoadStatus.Loading && uiState.frames.isEmpty() -> {
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

                else -> when (selectedTab) {
                    0 -> FramesTab(frames = uiState.frames, onOpenShop = onOpenShop)
                    1 -> SkinsTab(uiState = uiState, onApply = onApplySkin, onOpenShop = onOpenShop)
                    else -> EffectsTab(uiState = uiState, onApply = onApplyEffect, onOpenShop = onOpenShop)
                }
            }
        }
    }
}

/** Tab Frames — CHI XEM bo suu tap (khong dung toi luong chon khung luc dang bai). */
@Composable
private fun FramesTab(frames: List<FrameDto>, onOpenShop: () -> Unit) {
    if (frames.isEmpty()) {
        EmptyHint("No frames yet — visit the Shop to get some!")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ShopLink("Get more frames in the Shop", onOpenShop) }
        items(frames, key = { it.frameId }) { frame ->
            CollectibleItem(
                name = frame.frameName,
                // Khung khong co khai niem "dang dung" o man nay (chon khung nam
                // o luong dang bai) -> chi co OWNED / LOCKED
                state = if (frame.isUnlocked) {
                    CollectibleState.OWNED
                } else {
                    CollectibleState.LOCKED
                },
                aspectRatio = 1f,
            ) {
                if (frame.imageUrl != null) {
                    AsyncImage(
                        model = frame.imageUrl,
                        contentDescription = frame.frameName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
            }
        }
    }
}

/** Tab Skins — luoi 2 cot, thumbnail doc 9:16. O dau tien luon la Default. */
@Composable
private fun SkinsTab(uiState: AppearanceUiState, onApply: (Int) -> Unit, onOpenShop: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ShopLink("Get more skins in the Shop", onOpenShop) }
        items(uiState.skins, key = { it.id }) { skin ->
            CollectibleItem(
                name = skin.displayName,
                state = when {
                    skin.id == uiState.currentSkinId -> CollectibleState.IN_USE
                    uiState.ownsSkin(skin.id) -> CollectibleState.OWNED
                    else -> CollectibleState.LOCKED
                },
                aspectRatio = 9f / 16f,
                onClick = { onApply(skin.id) },
            ) {
                SkinThumbnail(skin)
            }
        }
    }
}

/**
 * Tab Effects — luoi 2 cot, moi o la **khu vuc demo song**: cham vao chinh o do
 * thi hieu ung chay ngay trong o (SKIN_PLAN.md muc 4.2).
 *
 * O chua so huu VAN cham thu duoc (cho nguoi dung thay co dang mua o Shop
 * khong) nhung bam khong ap dung — chan o ViewModel.
 */
@Composable
private fun EffectsTab(uiState: AppearanceUiState, onApply: (Int) -> Unit, onOpenShop: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ShopLink("Get more effects in the Shop", onOpenShop) }
        items(uiState.effects, key = { it.id }) { effect ->
            // Bam o -> chay lai demo. Voi o CHUA so huu thi day la tac dung duy
            // nhat (ViewModel chan ap dung), nen thieu no la bam vao khong co gi
            // xay ra — dung ky vong "cham thu truoc khi quyet dinh mua".
            var replay by remember(effect.id) { mutableIntStateOf(0) }
            CollectibleItem(
                name = effect.displayName,
                state = when {
                    effect.id == uiState.currentEffectId -> CollectibleState.IN_USE
                    uiState.ownsEffect(effect.id) -> CollectibleState.OWNED
                    else -> CollectibleState.LOCKED
                },
                aspectRatio = 1f,
                onClick = {
                    replay++
                    onApply(effect.id)
                },
            ) {
                EffectDemoCell(effect = effect, replay = replay)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = SkinTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Dong "Get more ... in the Shop" dau moi tab (2026-11-15 — thay cho loi nhac
 * "quay gacha"): vat pham khoa gio mua o Shop, day la loi tat sang do.
 */
@Composable
private fun ShopLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$text  →",
            color = SkinTheme.colors.accent,
            fontWeight = FontWeight.Bold,
        )
    }
}
