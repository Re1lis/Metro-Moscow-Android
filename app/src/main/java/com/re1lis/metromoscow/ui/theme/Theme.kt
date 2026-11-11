package com.re1lis.metromoscow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.re1lis.metromoscow.data.ThemeMode
import com.re1lis.metromoscow.data.UserSettings

val MetroRed = Color(0xFFD71920)
val MetroBlue = Color(0xFF1565C0)
val MetroGreen = Color(0xFF008F5A)
val MetroPurple = Color(0xFF6D4C9F)
val MetroOrange = Color(0xFFE76F00)

val AccentColors = listOf(MetroRed, MetroBlue, MetroGreen, MetroPurple, MetroOrange)

@Composable
fun MetroTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = AccentColors.getOrElse(settings.accentIndex) { MetroRed }
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            secondary = accent.copy(alpha = .85f),
            surface = Color(0xFF15171B),
            surfaceContainer = Color(0xFF202329),
            background = Color(0xFF101216)
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent.copy(alpha = .85f),
            surface = Color(0xFFFFFBFF),
            surfaceContainer = Color(0xFFF3F0F4),
            background = Color(0xFFF9F7FA)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
