package com.pomodoro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Modern Color Palette ---

// Primary: Warm coral/tomato (referencing the Pomodoro tomato)
val PrimaryLight = Color(0xFFE85D4A)
val PrimaryDark = Color(0xFFFF7B6B)

// Secondary: Calming teal
val SecondaryLight = Color(0xFF2EC4B6)
val SecondaryDark = Color(0xFF5EEAD4)

// Backgrounds
val BackgroundLight = Color(0xFFF8F6F4)
val BackgroundDark = Color(0xFF1A1A2E)

// Surfaces
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF242442)

// On-colors
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnPrimaryDark = Color(0xFF1A1A2E)

val OnBackgroundLight = Color(0xFF1C1B1F)
val OnBackgroundDark = Color(0xFFE6E1E5)

val OnSurfaceLight = Color(0xFF1C1B1F)
val OnSurfaceDark = Color(0xFFE6E1E5)

// Error
val ErrorLight = Color(0xFFB3261E)
val ErrorDark = Color(0xFFF2B8B5)

// Mode-specific accent colors
val FocusColor = Color(0xFFE85D4A)       // Warm red/coral
val ReviewColor = Color(0xFFF4A261)       // Amber/orange
val BreakColor = Color(0xFF2EC4B6)        // Teal/green

val FocusColorDark = Color(0xFFFF7B6B)
val ReviewColorDark = Color(0xFFFFBF7A)
val BreakColorDark = Color(0xFF5EEAD4)

// Track colors (for circular progress background)
val TrackLight = Color(0xFFE8E4E0)
val TrackDark = Color(0xFF3A3A5C)

private val LightColorScheme = lightColors(
    primary = PrimaryLight,
    primaryVariant = Color(0xFFC44536),
    secondary = SecondaryLight,
    secondaryVariant = Color(0xFF1FA396),
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorLight,
    onPrimary = OnPrimaryLight,
    onSecondary = Color(0xFFFFFFFF),
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    onError = Color(0xFFFFFFFF)
)

private val DarkColorScheme = darkColors(
    primary = PrimaryDark,
    primaryVariant = Color(0xFFFF9E91),
    secondary = SecondaryDark,
    secondaryVariant = Color(0xFF7DFFD4),
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorDark,
    onPrimary = OnPrimaryDark,
    onSecondary = Color(0xFF1A1A2E),
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onError = Color(0xFF601410)
)

@Composable
fun PomodoroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colors = colors,
        typography = PomodoroTypography,
        shapes = PomodoroShapes,
        content = content
    )
}
