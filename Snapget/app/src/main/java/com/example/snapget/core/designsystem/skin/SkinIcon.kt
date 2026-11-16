package com.example.snapget.core.designsystem.skin

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Ve icon cua skin, TU FALLBACK ve icon Material khi skin chua ve icon do.
 *
 * Day la chia khoa de lam dan: them 1 skin moi chi voi 3 icon van chay duoc
 * toan app, 9 icon con lai tu dung ban Material nhu hien tai. Khong co man nao
 * vo vi thieu asset.
 *
 * ```
 * SkinIcon(SkinTheme.icons.camera, Icons.Outlined.PhotoCamera, "Camera")
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
    // LUON co kich thuoc (fix 2026-11-16): icon skin dang WebP trong drawable-nodpi
    // (Snow: 192-288px) co intrinsic size = so PIXEL -> khong truyen size thi Icon ve
    // theo 192px roi bi chan boi cho chua -> cho to cho nho, va lech han icon vector
    // (Forest, 24dp). `size` dat SAU modifier cua caller: caller da co size thi size
    // cua caller thang (rang buoc ngoai), chua co thi dung [SkinIconSize.Medium].
    val sized = modifier.size(SkinIconSize.Medium)
    if (res != null) {
        Icon(
            painter = painterResource(res),
            contentDescription = contentDescription,
            modifier = sized,
            tint = Color.Unspecified,
        )
    } else {
        Icon(
            imageVector = fallback,
            contentDescription = contentDescription,
            modifier = sized,
            tint = tint,
        )
    }
}

/**
 * Kich thuoc CHUAN cho icon skin (2026-11-16) — moi cho goi [SkinIcon] chon 1 bac
 * theo VAI TRO, khong tu dat so le (truoc: send 20dp o chat, 26dp o feed; chevron
 * nhom khong co size, chevron 1-1 16dp -> cung 1 man ma icon to nho khac nhau).
 */
object SkinIconSize {
    /** Chevron/mui ten trang tri cuoi dong danh sach. */
    val Inline = 16.dp

    /** Icon nho canh chu (pill, dong phu). */
    val Small = 20.dp

    /** Mac dinh: top bar, nut trong IconButton, nut gui o o nhap. */
    val Medium = 24.dp

    /** Nut hanh dong lon tren feed / xem bai (grid, ⋯). */
    val Large = 32.dp
}
