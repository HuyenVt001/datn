package com.example.snapget.core.designsystem.component.collectible

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapget.core.designsystem.effect.TouchEffect
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.effect.drawTouchEffectFrame
import com.example.snapget.core.designsystem.effect.rememberEffectSheet
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.SkinTheme

/*
 * Phan "hinh" ve trong [CollectibleItem] cho skin va hieu ung cham — dung CHUNG
 * cho man Appearance va man Shop (tach ra core/ 2026-11-15 vi dung o 2 feature).
 * Asset cua skin/hieu ung nam trong APK nen app tu ve, khong can anh tu server.
 */

/**
 * Xem truoc skin. Chua co anh thumbnail thi ve o mau bang CHINH token cua skin
 * do — van thay duoc bang mau that, khong phai o trong.
 */
@Composable
fun SkinThumbnail(skin: AppSkin, modifier: Modifier = Modifier) {
    if (skin.thumbnail != null) {
        Image(
            painter = painterResource(skin.thumbnail),
            contentDescription = skin.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize().background(skin.colors.background).padding(10.dp),
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
 * O demo hieu ung: chay 1 vong khi o xuat hien, va chay lai moi lan [replay]
 * doi (o duoc bam).
 *
 * Khong dung thumbnail tinh — nguoi dung thay dung thu se nhan duoc, khong phai
 * ap dung (hay mua) roi moi biet.
 */
@Composable
fun EffectDemoCell(effect: TouchEffect, replay: Int, modifier: Modifier = Modifier) {
    if (effect.id == TouchEffectRegistry.NONE_ID) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        modifier = modifier.fillMaxSize().clip(SkinTheme.shapes.input),
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
