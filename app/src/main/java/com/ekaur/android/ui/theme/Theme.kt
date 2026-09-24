package com.ekaur.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light only. One soft, warm theme -- a second dark palette would be a whole
// separate set of decisions for a screen most people open in daylight, and the
// gradient carries the mood either way.
private val EkAurColors = lightColorScheme(
    primary = Grad2,            // a mid-gradient purple, for Material's own bits
    onPrimary = Ink,           // white, on the accent
    secondary = Grad3,
    onSecondary = Ink,
    error = Heat,
    onError = Ink,
    background = Canvas,
    onBackground = Chalk,
    surface = Surface,
    onSurface = Chalk,
    surfaceVariant = SurfaceLav,
    onSurfaceVariant = Smoke,
    outline = InkLine,
)

@Composable
fun EkAurTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Dark system-bar icons, because both bars sit on a light canvas.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = EkAurColors,
        typography = Typography,
        content = content,
    )
}
