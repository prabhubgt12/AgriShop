package com.ledge.splitbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography

// CashBook-like palette: purple app bar, white/black cards, and off-white / very-dark backgrounds
private val PrimaryLight = Color(0xFF6B46C1)
private val PrimaryDark = Color(0xFF8B6CF7)
private val OnPrimary = Color(0xFFFFFFFF)
private val Secondary = Color(0xFF64748B)
private val OnSecondary = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    surface = Color(0xFFFFFFFF),
    surfaceTint = Color.Transparent,
    onSurface = Color(0xFF121316),
    background = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFF49454F),
    primaryContainer = Color(0xFFE9D7FF),
    onPrimaryContainer = Color(0xFF2D0A73),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Secondary,
    onSecondary = OnSecondary,
    surface = Color(0xFF1E1E1E),
    surfaceTint = Color.Transparent,
    onSurface = Color(0xFFFFFFFF),
    background = Color(0xFF121212),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFB3B3B3),
    primaryContainer = Color(0xFF2F2556),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondaryContainer = Color(0xFF2F3037),
    onSecondaryContainer = Color(0xFFEDEDF3)
)

@Composable
fun SimpleSplitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8),
        small = RoundedCornerShape(10),
        medium = RoundedCornerShape(14),
        large = RoundedCornerShape(14),
        // AlertDialog uses extraLarge by default; keep it minimal to avoid overly rounded corners
        extraLarge = RoundedCornerShape(12)
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = shapes,
        content = content
    )
}
