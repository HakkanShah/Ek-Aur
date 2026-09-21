package com.ekaur.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

/**
 * The Instagram gradient, in one place.
 *
 * A diagonal sweep across the five brand stops. Used on the hero number, the
 * wordmark, primary buttons, avatar rings and the active tab -- the one accent
 * that makes the app read as Instagram-adjacent without copying it outright.
 */
fun instaGradient(): Brush = Brush.linearGradient(
    colors = InstaStops,
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** A softer wash of the same, for large fills that should not shout. */
fun instaGradientSoft(): Brush = Brush.linearGradient(
    colors = listOf(
        SurfaceLav,
        SurfaceBlush,
        SurfacePeach,
    ),
)

fun Modifier.gradientBackground(shape: Shape): Modifier =
    background(brush = instaGradient(), shape = shape)
