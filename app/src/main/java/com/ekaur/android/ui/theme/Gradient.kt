package com.ekaur.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

/**
 * The Instagram gradient, in one place.
 *
 * A diagonal sweep across the five brand stops. The brush is size-relative
 * (it spans whatever it paints, via [Offset.Infinite]), so one instance serves
 * every call site -- it used to be rebuilt on every call, 20-odd times a frame.
 */
private val InstaBrush: Brush = Brush.linearGradient(
    colors = InstaStops,
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** The same sweep without the pale end, so white text on it stays legible. */
private val ButtonBrush: Brush = Brush.linearGradient(
    colors = ButtonStops,
    start = Offset.Zero,
    end = Offset.Infinite,
)

private val SoftBrush: Brush = Brush.linearGradient(
    colors = listOf(SurfaceLav, SurfaceBlush, SurfacePeach),
)

fun instaGradient(): Brush = InstaBrush

/** For fills that carry white text: buttons, badges, the active tab. */
fun buttonGradient(): Brush = ButtonBrush

/** A softer wash of the same, for large fills that should not shout. */
fun instaGradientSoft(): Brush = SoftBrush

fun Modifier.gradientBackground(shape: Shape): Modifier =
    background(brush = instaGradient(), shape = shape)
