package com.chordx.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF9A4A00),
    onTertiary = Color.White,
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBFAF7),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainer = Color(0xFFEFEEEA),
    surfaceContainerHigh = Color(0xFFE9E8E4),
    outline = Color(0xFF74777F),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004786),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4F2500),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF111317),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ChordXTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Keep status/navigation bar icons readable when the app theme differs from the system theme.
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
