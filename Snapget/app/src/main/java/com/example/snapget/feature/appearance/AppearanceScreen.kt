package com.example.snapget.feature.appearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.collectible.CollectibleItem
import com.example.snapget.core.designsystem.component.collectible.CollectibleState
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.effect.TouchEffect
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.effect.drawTouchEffectFrame
import com.example.snapget.core.designsystem.effect.rememberEffectSheet
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.network.dto.FrameDto

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

    Scaffold(
        topBar = {
            SimpleTopBar(title = "Appearance", onBackClick = { navController.popBackStack() })
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
                        onClick = { selectedTab = index },
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
                        TextButton(onClick = { viewModel.load() }) {
                            Text(
                                text = "Retry",
                                color = SkinTheme.colors.accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                else -> when (selectedTab) {
                    0 -> FramesTab(frames = uiState.frames)
                    1 -> SkinsTab(uiState = uiState, onApply = viewModel::applySkin)
                    else -> EffectsTab(uiState = uiState, onApply = viewModel::applyEffect)
                }
            }
        }
    }
}

/** Tab Frames — CHI XEM bo suu tap (khong dung toi luong chon khung luc dang bai). */
@Composable
private fun FramesTab(frames: List<FrameDto>) {
    if (frames.isEmpty()) {
        EmptyHint("No frames yet — roll the gacha to collect them!")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
private fun SkinsTab(uiState: AppearanceUiState, onApply: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
 * Xem truoc skin. Chua co anh thumbnail thi ve o mau bang CHINH token cua skin
 * do — van thay duoc bang mau that, khong phai o trong.
 */
@Composable
private fun SkinThumbnail(skin: AppSkin) {
    if (skin.thumbnail != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(skin.thumbnail),
            contentDescription = skin.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().background(skin.colors.background).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .clip(skin.shapes.image).background(skin.colors.surface),
        )
        Box(
            Modifier.fillMaxWidth().weight(0.35f)
                .clip(skin.shapes.pill).background(skin.colors.pill),
        )
        Box(
            Modifier.fillMaxWidth().weight(0.35f)
                .clip(skin.shapes.pill).background(skin.colors.accent),
        )
    }
}

/**
 * Tab Effects — luoi 2 cot, moi o la **khu vuc demo song**: cham vao chinh o do
 * thi hieu ung chay ngay trong o (SKIN_PLAN.md muc 4.2).
 *
 * O chua so huu VAN cham thu duoc (cho nguoi dung thay co dang quay gacha
 * khong) nhung bam khong ap dung — chan o ViewModel.
 */
@Composable
private fun EffectsTab(uiState: AppearanceUiState, onApply: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(uiState.effects, key = { it.id }) { effect ->
            // Bam o -> chay lai demo. Voi o CHUA so huu thi day la tac dung duy
            // nhat (ViewModel chan ap dung), nen thieu no la bam vao khong co gi
            // xay ra — dung ky vong "cham thu truoc khi quyet dinh quay".
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

/**
 * O demo hieu ung: chay 1 vong khi o xuat hien, va chay lai moi lan [replay]
 * doi (o duoc bam).
 *
 * Khong dung thumbnail tinh — nguoi dung thay dung thu se nhan duoc, khong phai
 * ap dung roi moi biet.
 */
@Composable
private fun EffectDemoCell(effect: TouchEffect, replay: Int) {
    if (effect.id == TouchEffectRegistry.NONE_ID) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "—", color = SkinTheme.colors.textSecondary, fontSize = 24.sp)
        }
        return
    }

    /*
     * `Animatable` + `snapTo(0)` chu KHONG phai `animateFloatAsState` toi mot
     * `round` tang mai.
     *
     * Ban cu: moi lan bam thi `round++` va animation chay toi gia tri moi. Bam
     * 10 phat trong 1 giay -> `round = 10` trong khi `progress` moi toi ~1.3,
     * `cycle = progress - (round - 1)` am ca chuc -> **o demo tat ngum** cho toi
     * khi animation duoi kip. Nhin y het "bam nhieu qua thi lag/dung hinh".
     *
     * Ban moi: moi lan bam la ve 0 roi chay lai — luon dung 1 animation song,
     * bam bao nhieu lan cung the.
     */
    val progress = remember { Animatable(0f) }
    val sheet = rememberEffectSheet(effect)

    // Chay 1 vong ngay khi o xuat hien -> luot qua tab la thay moi o cung dien;
    // moi lan bam o (replay doi) thi chay lai tu dau.
    LaunchedEffect(replay, effect.id) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(effect.durationMs.coerceAtLeast(1), easing = LinearEasing),
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().clip(SkinTheme.shapes.input),
    ) {
        // graphicsLayer: o demo tu ve tren RenderNode rieng, animation chay khong
        // lam ban va bat `LazyVerticalGrid` ghi lai display list moi frame.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            // Doc `progress.value` TRONG lambda ve -> chi pha ban khau ve,
            // khong keo theo recomposition.
            val cycle = progress.value
            if (sheet != null && cycle > 0f && cycle < 1f) {
                /*
                 * Co ve tinh theo O DEMO chu khong theo `effect.sizeDp`: o trong
                 * luoi nho hon nhieu so voi co hieu ung chay that tren man hinh,
                 * lay dung `sizeDp` la animation tran ra ngoai o va bi `clip` cat.
                 */
                drawTouchEffectFrame(
                    effect = effect,
                    sheet = sheet,
                    origin = Offset(size.width / 2f, size.height / 2f),
                    progress = cycle,
                    sizePx = minOf(size.width, size.height) * 0.9f,
                )
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
