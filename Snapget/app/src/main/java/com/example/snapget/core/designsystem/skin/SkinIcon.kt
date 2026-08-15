package com.example.snapget.core.designsystem.skin

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource

/**
 * Ve icon cua skin, TU FALLBACK ve icon Material khi skin chua ve icon do.
 *
 * Day la chia khoa de lam dan: them 1 skin moi chi voi 3 icon van chay duoc
 * toan app, 9 icon con lai tu dung ban Material nhu hien tai. Khong co man nao
 * vo vi thieu asset.
 *
 * ```
 * SkinIcon(SkinTheme.icons.camera, Icons.Filled.PhotoCamera, "Camera")
 * ```
 *
 * ⚠️ **Icon rieng cua skin ve NGUYEN MAU GOC** (`Color.Unspecified` — chot voi
 * user 2026-08-14): designer to mau san cho ca bo icon, khop bang mau cua skin
 * do; app tint vao la de bet het ve 1 mau, mat luon phan phoi mau da thiet ke.
 * [tint] CHI con tac dung o nhanh fallback Material — icon Material la vector
 * don sac, khong tint thi chim vao nen.
 *
 * @param res id drawable cua skin (`SkinTheme.icons.xxx`) — null = chua ve.
 * @param fallback icon Material dang dung o cho do.
 * @param tint mau cho nhanh fallback Material; KHONG ap cho icon cua skin.
 */
@Composable
fun SkinIcon(
    @DrawableRes res: Int?,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = SkinTheme.colors.textPrimary,
) {
    if (res != null) {
        Icon(
            painter = painterResource(res),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = Color.Unspecified,
        )
    } else {
        Icon(
            imageVector = fallback,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}
