package com.example.snapget.core.designsystem.component.button

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.skin.SkinTheme

/**
 * Nut chup 80dp — "bo mat" cua skin (SKIN_PLAN.md muc 6.13.2).
 *
 * Skin co khai `SkinImages.captureButton` thi ve ANH do: anh ve ca vien lan
 * ruot nen [Circle] chi con lam khung bat cham, khong ve vien/nen gi them.
 * Skin chua co anh (vd `DefaultSkin`) -> vong tron ruot trang + vien accent
 * nhu cu.
 *
 * Dung chung cho MOI cho co nut nay — bottom bar man camera, hang nut day feed,
 * post detail, man chup chung coop — de doi skin la ca 4 cho doi cung luc
 * (truoc 2026-08-14 chi bottom bar an anh cua skin, 3 cho kia van la vong tron
 * ve bang code nen nhin lech han nhau).
 *
 * @param size cho ca nut (mac dinh 80dp — nut chup chinh).
 * @param onLongPress GIU nut = quay "anh GIF"; de null thi nut chi nhan tap.
 * @param onPressRelease THA tay = dung quay.
 */
@Composable
fun CaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    contentDescription: String? = null,
    onLongPress: (() -> Unit)? = null,
    onPressRelease: (() -> Unit)? = null,
) {
    val captureImage = SkinTheme.images.captureButton

    if (captureImage != null) {
        Circle(
            outerSize = size,
            gap = 0.dp,
            backgroundColor = Color.Transparent,
            borderWidth = 0.dp,
            borderColor = Color.Transparent,
            onClick = onClick,
            modifier = modifier,
            innerContent = {
                Image(
                    painter = painterResource(captureImage),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            onLongPress = onLongPress,
            onPressRelease = onPressRelease,
        )
    } else {
        Circle(
            outerSize = size,
            gap = 7.dp,
            backgroundColor = Color.Transparent,
            borderWidth = 3.dp,
            borderColor = SkinTheme.colors.accent,
            onClick = onClick,
            modifier = modifier,
            onLongPress = onLongPress,
            onPressRelease = onPressRelease,
        )
    }
}
