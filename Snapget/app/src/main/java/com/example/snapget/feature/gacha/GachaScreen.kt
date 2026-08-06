package com.example.snapget.feature.gacha

import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.snapget.R
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
                rollingTimes = uiState.rollingTimes,
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

/**
 * Bo cuc bam theo **ban thiet ke goc** `Sources/skin-assets/gacha/PreviewGachaScreen.png`:
 * khung so Astrite da duoc ve san trong `gacha_bg`, 2 nut quay la asset rieng.
 *
 * ⚠️ Chu/icon trong man nay nam DE LEN anh nen (khong doi theo skin) nen dung
 * mau trang co dinh — dung quy tac "trang vi nam tren anh".
 */
@Composable
private fun GachaContent(
    state: GachaStateDto,
    rollingTimes: Int?,
    isAwaitingPayment: Boolean,
    onBack: () -> Unit,
    onShowRules: () -> Unit,
    onRoll: (Int) -> Unit,
    onTopup: () -> Unit,
    onCancelPayment: () -> Unit,
) {
    val density = LocalDensity.current
    val isRolling = rollingTimes != null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val bg = remember(constraints.maxWidth, constraints.maxHeight, density) {
            BgAnchor(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density)
        }

        Image(
            painter = painterResource(R.drawable.gacha_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // TopCenter chu khong Center: hang header (khung so Astrite ve san
            // trong anh) phai luon dinh mep tren, khong duoc cat mat.
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )

        // Dai mo duoi chan man de chu canh bao / chip pity doc duoc tren art sang
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    ),
                ),
        )

        // ==== Hang header — neo theo TOA DO TRONG ANH NEN ====
        // GlassIconButton/AstriteBar tu no rong vung cham ra toi thieu 48dp
        // quanh TAM duoc truyen vao — phan ve giu nguyen kich thuoc.
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            centerX = bg.x(BACK_CENTER_X),
            centerY = bg.y(BAR_CENTER_Y),
            diameter = bg.len(ICON_DIAMETER),
            onClick = onBack,
        )

        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "Gacha rules",
            centerX = bg.x(RULES_CENTER_X),
            centerY = bg.y(BAR_CENTER_Y),
            diameter = bg.len(ICON_DIAMETER),
            onClick = onShowRules,
        )

        AstriteBar(
            astrite = state.astrite,
            bg = bg,
            onTopup = onTopup,
        )

        if (isAwaitingPayment) {
            PendingPaymentBanner(
                onCancel = onCancelPayment,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = bg.y(BAR_BOTTOM + 30f))
                    .padding(horizontal = 16.dp),
            )
        }

        // ==== 2 nut quay — neo theo DAY MAN HINH ====
        // Khong neo theo anh: may ti le thap hon 19.5:9 bi cat mat phan duoi anh,
        // neo theo anh la nut nam duoi day man hinh, bam khong toi.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bg.len(ROLL_BTN_BOTTOM)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Chip pity SSR ngay tren nut quay — dung ngu canh "con bao nhieu
            // luot nua chac chan ra SSR" truoc khi bam (CHI hien SSR, user chot)
            Text(
                text = "SSR pity ${state.pity.SSR}/${state.pityLimit.SSR}",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(SkinTheme.shapes.pill)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = bg.len(ROLL_BTN_LEFT)),
                horizontalArrangement = Arrangement.spacedBy(bg.len(ROLL_BTN_GAP)),
            ) {
                // Spinner chi hien tren DUNG nut vua bam (`rollingTimes`), nut
                // con lai mo di — hai spinner cung quay nhin nhu app treo.
                RollButton(
                    background = R.drawable.gacha_1rollbutton,
                    times = 1,
                    cost = state.costSingle,
                    enabled = !isRolling && state.astrite >= state.costSingle,
                    loading = rollingTimes == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { onRoll(1) },
                )
                RollButton(
                    background = R.drawable.gacha_10rollbutton,
                    times = state.tenTimes,
                    cost = state.costTen,
                    enabled = !isRolling && state.astrite >= state.costTen,
                    loading = rollingTimes != null && rollingTimes != 1,
                    modifier = Modifier.weight(1f),
                    onClick = { onRoll(state.tenTimes) },
                )
            }

            if (state.astrite < state.costSingle) {
                Text(
                    text = "Not enough Astrite — finish your daily quests, or tap the balance to top up.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
    }
}

/**
 * So Astrite ve DE LEN khung da co san trong `gacha_bg`, kem nut `+` o mep phai
 * khung (user chot vi tri).
 *
 * Ca thanh bam duoc chu khong rieng dau `+`: vung cham cua dau `+` chi ~24dp,
 * qua nho de bam trung. Vung cham keo cao toi thieu 48dp (khung ve san chi cao
 * ~24dp — duoi chuan cham cua Material) nhung phan VE van bam dung khung.
 */
@Composable
private fun AstriteBar(
    astrite: Int,
    bg: BgAnchor,
    onTopup: () -> Unit,
) {
    val barHeight = bg.len(BAR_BOTTOM - BAR_TOP)
    val touchHeight = max(barHeight, 48.dp)

    Box(
        modifier = Modifier
            .offset(
                x = bg.x(BAR_LEFT),
                y = bg.y(BAR_CENTER_Y) - touchHeight / 2,
            )
            .width(bg.len(BAR_RIGHT - BAR_LEFT + PLUS_DIAMETER / 2f))
            .height(touchHeight)
            .clip(SkinTheme.shapes.pill)
            .clickable(onClick = onTopup),
    ) {
        val label = "%,d".format(astrite)
        Text(
            text = label,
            color = Color.White,
            // Be ngang con lai giua vien pha le (ve san) va nut `+` chi ~145px
            // trong he anh nen. Co chu giam dan theo do dai de so tien to (goi
            // nap 5.201.314) khong bi cat mat chu so.
            fontSize = when {
                label.length <= 6 -> 15.sp
                label.length <= 8 -> 12.sp
                label.length <= 10 -> 10.sp
                else -> 8.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = bg.len(BAR_TEXT_LEFT - BAR_LEFT))
                .width(bg.len(BAR_RIGHT - BAR_TEXT_LEFT - PLUS_DIAMETER / 2f - 6f)),
        )

        // Tam dau `+` dat DUNG mep phai khung (x = BAR_RIGHT) — nua trong nua
        // ngoai vien pill, kieu nut nap cua cac game gacha. Vi the o chu ben
        // trai chi duoc keo toi `BAR_RIGHT - PLUS/2 - 6` la dung mep trai icon.
        Icon(
            imageVector = Icons.Filled.AddCircle,
            contentDescription = "Top up Astrite",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(bg.len(PLUS_DIAMETER)),
        )
    }
}

/**
 * Icon tron nen kinh mo — de doc tren moi vung cua anh nen.
 *
 * Nhan TAM (theo toa do anh nen da quy doi) thay vi goc trai tren: vung cham
 * duoc rong ra toi thieu 48dp quanh tam do (chuan Material), con hinh tron VE
 * van dung [diameter] — 2 kich thuoc doc lap nhau.
 */
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    centerX: Dp,
    centerY: Dp,
    diameter: Dp,
    onClick: () -> Unit,
) {
    val touch = max(diameter, 48.dp)
    Box(
        modifier = Modifier
            .offset(x = centerX - touch / 2, y = centerY - touch / 2)
            .size(touch)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(diameter * 0.55f),
            )
        }
    }
}

/**
 * Nut quay — nen la asset that (`gacha_1rollbutton` / `gacha_10rollbutton`),
 * chu do code ve len vi asset la pill TRON khong co chu.
 *
 * `FillBounds` chu khong `Fit`: file SVG goc co transform lam anh cao hon ti le
 * cua bitmap nhung trong; giu dung khung 386×139 cua ban thiet ke moi ra dung
 * hinh dang trong `PreviewGachaScreen.png`.
 */
@Composable
private fun RollButton(
    @DrawableRes background: Int,
    times: Int,
    cost: Int,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 8f)

    Box(
        modifier = modifier
            .aspectRatio(ROLL_BTN_W / ROLL_BTN_H)
            .clip(RoundedCornerShape(percent = 50))
            // Nut DANG quay van sang binh thuong (spinner da noi "cho ti") —
            // chi mo khi bi khoa vi ly do khac (het tien / nut kia dang quay)
            .alpha(if (enabled || loading) 1f else 0.45f)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "x$times",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(shadow = shadow),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_astrite),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "%,d".format(cost),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(shadow = shadow),
                    )
                }
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
                RuleRow("Duplicate SSR", "+${state.refunds.SSR}", GachaRarity.SSR, astriteIcon = true)
                RuleRow("Duplicate SR", "+${state.refunds.SR}", GachaRarity.SR, astriteIcon = true)
                RuleRow("Duplicate R", "+${state.refunds.R}", GachaRarity.R, astriteIcon = true)

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

/** Hang thong tin trong popup Rule. [astriteIcon] = them icon Astrite sau gia tri. */
@Composable
private fun RuleRow(label: String, value: String, color: Color, astriteIcon: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(text = value, color = SkinTheme.colors.textPrimary)
        if (astriteIcon) {
            Spacer(Modifier.width(4.dp))
            Image(
                painter = painterResource(R.drawable.ic_astrite),
                contentDescription = "Astrite",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// `GachaBackdropPlaceholder` da XOA (2026-08-06): no chi la vien mo giu cho
// trong luc cho anh nen gacha that. Gio `gacha_bg.png` da vao APK nen khong con
// cho nao goi toi.
