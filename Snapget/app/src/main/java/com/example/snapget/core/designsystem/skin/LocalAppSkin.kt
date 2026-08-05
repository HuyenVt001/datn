package com.example.snapget.core.designsystem.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.snapget.core.designsystem.skin.skins.DefaultSkin

/**
 * Skin dang ap dung. Dung `staticCompositionLocalOf` (khong phai
 * `compositionLocalOf`): skin doi RAT it, va khi doi thi ve lai ca cay — re hon
 * nhieu so voi theo doi tung diem doc.
 */
val LocalAppSkin = staticCompositionLocalOf { DefaultSkin }

/**
 * Cong doc token cua skin dang dung — dat ten/cach dung giong
 * `MaterialTheme.colorScheme` cho quen tay:
 *
 * ```
 * Text(color = SkinTheme.colors.textPrimary)
 * Box(Modifier.clip(SkinTheme.shapes.image))
 * ```
 */
object SkinTheme {
    val skin: AppSkin
        @Composable @ReadOnlyComposable
        get() = LocalAppSkin.current

    val colors: SkinColors
        @Composable @ReadOnlyComposable
        get() = LocalAppSkin.current.colors

    val icons: SkinIcons
        @Composable @ReadOnlyComposable
        get() = LocalAppSkin.current.icons

    val shapes: SkinShapes
        @Composable @ReadOnlyComposable
        get() = LocalAppSkin.current.shapes

    val images: SkinImages
        @Composable @ReadOnlyComposable
        get() = LocalAppSkin.current.images
}
