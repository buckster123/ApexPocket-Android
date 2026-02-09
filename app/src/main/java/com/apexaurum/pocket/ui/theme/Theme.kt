package com.apexaurum.pocket.ui.theme

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val ApexDarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = ApexBlack,
    primaryContainer = GoldDark,
    onPrimaryContainer = GoldLight,
    secondary = VajraBlue,
    onSecondary = ApexBlack,
    tertiary = ElysianViolet,
    onTertiary = ApexBlack,
    background = ApexBlack,
    onBackground = TextPrimary,
    surface = ApexDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = ApexSurface,
    onSurfaceVariant = TextSecondary,
    outline = ApexBorder,
)

@Composable
fun ApexPocketTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as ComponentActivity
            val darkScrim = ApexBlack.toArgb()
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(darkScrim),
                navigationBarStyle = SystemBarStyle.dark(darkScrim),
            )
        }
    }

    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        content = content,
    )
}
