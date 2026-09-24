package com.ekaur.android.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * The app's motion vocabulary. Every animation picks from these, so a card
 * opening, a number counting and a tab gliding all feel like one family
 * instead of a dozen hand-tuned durations.
 */
object Motion {
    /** Presses, toggles, colour flips. */
    const val QUICK = 150

    /** Most state changes: expanding, swapping content, sliding indicators. */
    const val STANDARD = 260

    /** Entrances and anything the eye should follow. */
    const val EMPHASISED = 420

    /** How far apart staggered items start. */
    const val STAGGER = 45

    /** Material's "emphasised decelerate": fast out of the gate, soft landing. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    val Standard = FastOutSlowInEasing

    fun <T> quick() = tween<T>(QUICK, easing = Standard)
    fun <T> standard() = tween<T>(STANDARD, easing = Standard)
    fun <T> emphasised() = tween<T>(EMPHASISED, easing = Decelerate)

    /** A gentle overshoot, for things that should land with a little life. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
