package com.ledge.cashbook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// SplitBook-like palette: purple app bar, white/black cards, and off-white / very-dark backgrounds
private val PrimaryColor = Color(0xFF6750A4)  // Original purple used for both modes
private val OnPrimary = Color(0xFFFFFFFF)
private val Secondary = Color(0xFF64748B)
private val OnSecondary = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = PrimaryColor,  // Original purple
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    surface = Color(0xFFFFFFFF),  // Same as SplitBook
    surfaceTint = Color.Transparent,
    onSurface = Color(0xFF121316),
    background = Color(0xFFE8E8E8),  // More gray background
    surfaceVariant = Color(0xFFF7F7F7),  // Same as SplitBook
    onSurfaceVariant = Color(0xFF49454F),
    primaryContainer = Color(0xFFE9D7FF),
    onPrimaryContainer = Color(0xFF2D0A73),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = PrimaryColor,  // Same original purple for dark mode too
    onPrimary = Color(0xFFFFFFFF),
    secondary = Secondary,
    onSecondary = OnSecondary,
    surface = Color(0xFF1A1A1A),  // Less grayish, more subtle
    surfaceTint = Color.Transparent,
    onSurface = Color(0xFFFFFFFF),
    background = Color(0xFF0A0A0A),  // Deeper black, less gray
    surfaceVariant = Color(0xFF1F1F1F),  // Subtle variant
    onSurfaceVariant = Color(0xFFB3B3B3),
    primaryContainer = Color(0xFF2F2556),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondaryContainer = Color(0xFF2F3037),
    onSecondaryContainer = Color(0xFFD8E2F1)
)

@Composable
fun CashBookTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
