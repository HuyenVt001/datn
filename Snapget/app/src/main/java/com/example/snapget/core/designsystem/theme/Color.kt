package com.example.snapget.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Black / Gray / White Theme Colors
val GrayPrimary = Color(0xFFF5F5F5) // Light gray for primary elements
val GrayPrimaryDark = Color(0xFF212121) // Dark gray for primary variant
val GraySecondary = Color(0xFF9E9E9E) // Medium gray for secondary
val GrayTertiary = Color(0xFFBDBDBD) // Lighter gray for tertiary

val GrayBackground = Color(0xFF121212) // Pure black background
val GraySurface = Color(0xFF1A1A1A) // Very dark gray surface
val GraySurfaceVariant = Color(0xFF2C2C2C) // Darker gray surface variant

val GrayOnBackground = Color(0xFFFFFFFF) // White text on background
val GrayOnSurface = Color(0xFFE0E0E0) // Light gray text on surface
val GrayOnPrimary = Color(0xFF121212) // Black text on primary
val GrayOnSecondary = Color(0xFF121212) // Black text on secondary
val GrayOnSurfaceVariant = Color(0xFFB0B0B0) // Mid-gray text on variant

val GrayError = Color(0xFFCF6679) // Keep same red-pink error
val GrayOnError = Color(0xFFFFFFFF) // White text on error

val BackgroundPreview = Color(0xFF424242).copy(alpha = 0.5f) // Darker gray for previews

// Light palette — doi xung voi palette Gray o tren (muc Theme trong Settings).
// Chi cac man dung colorScheme moi doi mau; man hardcode Color.White giu nguyen (chap nhan).
val LightPrimary = Color(0xFF212121) // Near-black primary (dao nguoc GrayPrimary)
val LightPrimaryContainer = Color(0xFFE0E0E0)
val LightSecondary = Color(0xFF757575)
val LightTertiary = Color(0xFF9E9E9E)

val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEEEEE)

val LightOnBackground = Color(0xFF121212)
val LightOnSurface = Color(0xFF1F1F1F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightOnSurfaceVariant = Color(0xFF616161)

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)

/**
 * Vang accent DUY NHAT cua app (selection/capture/gamification — DESIGN.md muc 2).
 * Doi brand thi sua 1 cho nay; KHONG hardcode Color.Yellow trong screen nua.
 */
val SnapYellow = Color.Yellow
