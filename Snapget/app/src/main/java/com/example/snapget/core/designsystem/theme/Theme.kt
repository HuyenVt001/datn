package com.example.snapget.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.snapget.core.designsystem.skin.AppSkin
import com.example.snapget.core.designsystem.skin.LocalAppSkin
import com.example.snapget.core.designsystem.skin.skins.DefaultSkin

/**
 * Theme cua app — **dark-first thuan** (giao dien Light da xoa han 2026-08-05,
 * SKIN_PLAN.md muc 4.4). Nguoi dung khong chon sang/toi nua, chi chon SKIN.
 *
 * Bom skin theo 2 duong song song:
 *  1. [LocalAppSkin] — token rieng cua app (mau theo vai tro, icon, shape, anh).
 *  2. `MaterialTheme.colorScheme` — de component M3 chua refactor (Button,
 *     Switch, TextField…) van doi mau theo skin.
 *
 * Tham so co gia tri mac dinh nen MOI `@Preview` cu goi `AppTheme { … }` van
 * chay nguyen, khong phai sua.
 */
@Composable
fun AppTheme(
    skin: AppSkin = DefaultSkin,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppSkin provides skin) {
        MaterialTheme(
            colorScheme = skin.colors.toColorScheme(),
            typography = Typography,
            content = content,
        )
    }
}
