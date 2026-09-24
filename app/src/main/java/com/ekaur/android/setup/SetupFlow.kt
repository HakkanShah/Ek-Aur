package com.ekaur.android.setup

/**
 * The guided setup, as a pure function of what has been granted.
 *
 * The wizard keeps no step counter of its own. Every time the grants are
 * re-read (the app coming back to the front), the step is derived again from
 * scratch, so a switch flipped in Settings moves the flow forward with nothing
 * to fall out of sync -- and a step for something already granted can never be
 * shown again.
 */
enum class SetupStep { Welcome, Accessibility, Overlay, Done }

object SetupFlow {

    /** The two grants without which nothing works, in the order they are asked. */
    const val REQUIRED = 2

    /**
     * The step to show now.
     *
     * [welcomed] is whether the user has pressed through the welcome screen
     * this time round; it exists only so the intro is not skipped for a fresh
     * install, and never pins the flow to something already granted.
     */
    fun nextStep(welcomed: Boolean, service: Boolean, overlay: Boolean): SetupStep = when {
        service && overlay -> SetupStep.Done
        !welcomed && !service -> SetupStep.Welcome
        !service -> SetupStep.Accessibility
        else -> SetupStep.Overlay
    }

    /** How many of the required grants are in, for the progress dots. */
    fun requiredDone(service: Boolean, overlay: Boolean): Int =
        listOf(service, overlay).count { it }

    /** "1 step left" / "2 steps left" / "You're all set." */
    fun statusLine(missing: Int): String = when (missing) {
        0 -> "You're all set."
        1 -> "1 step left"
        else -> "$missing steps left"
    }
}
