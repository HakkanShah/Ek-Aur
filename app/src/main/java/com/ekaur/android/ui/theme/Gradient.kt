package com.ekaur.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

/**
 * The look's signature gradient, in one place.
 *
 * A diagonal sweep across the palette's stops. The brushes are size-relative
 * and cached on the palette, so one instance serves every call site; reading
 * them reads the live palette, so a change of look repaints whatever used them.
 */
fun instaGradient(): Brush = Looks.palette.gradient

/** For fills that carry white text: buttons, badges, the active tab. */
fun buttonGradient(): Brush = Looks.palette.buttonGradient

/** A softer wash of the same, for large fills that should not shout. */
fun instaGradientSoft(): Brush = Looks.palette.softGradient

fun Modifier.gradientBackground(shape: Shape): Modifier =
    background(brush = instaGradient(), shape = shape)
