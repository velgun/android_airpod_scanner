package com.velgun.airpodscanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkBlue,
    secondary = LightBlue,
    tertiary = LightBlue,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = OnPrimary,
    onSecondary = OnPrimary,
    onTertiary = OnPrimary,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = LightBlue,
    tertiary = DarkBlue,
    background = BackgroundGray,
    surface = SurfaceGray,
    onPrimary = OnPrimary,
    onSecondary = OnPrimary,
    onTertiary = OnPrimary,
    onBackground = OnBackground,
    onSurface = OnSurface,
)

@Composable
fun AirpodScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar to transparent
            window.statusBarColor = Color.Transparent.toArgb()
            // Set navigation bar to transparent
            window.navigationBarColor = Color.Transparent.toArgb()
            // Set the appearance of the status bar icons
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            // Set the appearance of the navigation bar icons
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}