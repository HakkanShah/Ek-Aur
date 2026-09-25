package com.ekaur.android.ui.theme

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light only. One soft, warm theme in three colour sets -- a second dark palette
// would be a whole separate set of decisions for a screen most people open in
// daylight, and the gradient carries the mood either way.
private fun colorsFor(p: Palette) = lightColorScheme(
    primary = p.stops[1],      // a mid-gradient colour, for Material's own bits
    onPrimary = Ink,           // white, on the accent
    secondary = p.stops[2],
    onSecondary = Ink,
    error = Heat,
    onError = Ink,
    background = p.canvas,
    onBackground = Chalk,
    surface = Surface,
    onSurface = Chalk,
    surfaceVariant = p.soft,
    onSurfaceVariant = Smoke,
    outline = p.hairline,
)

@Composable
fun EkAurTheme(
    look: AppLook = AppLook.Instagram,
    content: @Composable () -> Unit,
) {
    // A change of look cross-fades every colour over a moment rather than
    // snapping: the palette is walked from the old to the new, and everything
    // that reads it follows.
    val target = Palette.of(look)
    LaunchedEffect(target) {
        val from = Looks.palette
        if (from === target) return@LaunchedEffect
        val t = Animatable(0f)
        t.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) {
            Looks.palette = Palette.lerp(from, target, value)
        }
        Looks.palette = target
    }
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
        colorScheme = colorsFor(Looks.palette),
        typography = Typography,
        content = content,
    )
}
