package com.ekaur.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark only. A light mode would mean a second set of design decisions for a
// screen nobody opens in daylight anyway.
private val EkAurColors = darkColorScheme(
    primary = Acid,
    onPrimary = Ink,
    secondary = AcidDim,
    onSecondary = Ink,
    error = Heat,
    onError = Ink,
    background = Ink,
    onBackground = Chalk,
    surface = InkRaised,
    onSurface = Chalk,
    surfaceVariant = InkLine,
    onSurfaceVariant = Smoke,
    outline = InkLine,
)

@Composable
fun EkAurTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = EkAurColors,
        typography = Typography,
        content = content,
    )
}
