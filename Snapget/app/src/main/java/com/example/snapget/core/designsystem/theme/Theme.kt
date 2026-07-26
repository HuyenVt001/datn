package com.example.snapget.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.snapget.core.model.ThemeMode

// Brown Dark Color Scheme
private val BrownDarkColorScheme = darkColorScheme(
    primary = GrayPrimary,
    onPrimary = GrayOnPrimary,
    primaryContainer = GrayPrimaryDark,
    onPrimaryContainer = GrayOnBackground,

    secondary = GraySecondary,
    onSecondary = GrayOnSecondary,
    secondaryContainer = GrayPrimaryDark,
    onSecondaryContainer = GrayOnBackground,

    tertiary = GrayTertiary,
    onTertiary = GrayOnPrimary,
    tertiaryContainer = GrayPrimaryDark,
    onTertiaryContainer = GrayOnBackground,

    background = GrayBackground,
    onBackground = GrayOnBackground,

    surface = GraySurface,
    onSurface = GrayOnSurface,
    surfaceVariant = GraySurfaceVariant,
    onSurfaceVariant = GrayOnSurfaceVariant,

    error = GrayError,
    onError = GrayOnError,
)

// Light scheme doi xung (muc Theme trong Settings — DARK van la mac dinh)
private val GrayLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnBackground,

    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightPrimaryContainer,
    onSecondaryContainer = LightOnBackground,

    tertiary = LightTertiary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightPrimaryContainer,
    onTertiaryContainer = LightOnBackground,

    background = LightBackground,
    onBackground = LightOnBackground,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,

    error = LightError,
    onError = LightOnError,
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.DARK, // dark van la mac dinh — preview/caller cu khong doi
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) BrownDarkColorScheme else GrayLightColorScheme,
        typography = Typography,
        content = content,
    )
}
