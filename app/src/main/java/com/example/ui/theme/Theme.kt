package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GreenWalkDarkColorScheme = darkColorScheme(
    primary = TrafficGreen,
    onPrimary = TextDark,
    primaryContainer = TrafficGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyan,
    onSecondary = TextDark,
    tertiary = TrafficAmber,
    onTertiary = TextDark,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = TrafficRed,
    onError = TextPrimary
)

@Composable
fun GreenWalkTheme(
    darkTheme: Boolean = true, // Default to AMOLED Dark as requested
    content: @Composable () -> Unit
) {
    val colorScheme = GreenWalkDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkBackground.toArgb()
                window.navigationBarColor = DarkBackground.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
