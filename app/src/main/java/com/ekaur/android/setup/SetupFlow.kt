package com.ekaur.android.setup

/**
 * The guided setup, as a pure function of what has been granted.
 *
 * The wizard keeps no step counter of its own. Every time the grants are
 * re-read (the app coming back to the front), the step is derived again from
 * scratch, so a switch flipped in Settings moves the flow forward with nothing
 * to fall out of sync -- and a step for something already granted can never be
 * shown again.
 *
 * The order is chosen so the hard part goes smoothly:
 * 1. **Overlay** first. It's one switch, and once it's on the app can float a
 *    guide over Settings for everything after.
 * 2. **Keep alive** (battery, and Autostart on Xiaomi, OPPO, realme, vivo)
 *    before accessibility. Those phones refuse to start a service for an app
 *    they're restricting, which left people with the switch on and nothing
 *    counting, toggling it over and over.
 * 3. **Unblock** ("Allow restricted settings"), on Android 13+ phones that
 *    installed the app from a file -- asked for up front, before the switch
 *    that it blocks, rather than after people hit the wall.
 * 4. **Accessibility**, the switch itself.
 * 5. **Restart**, only if the switch is on but the phone never started it.
 */
enum class SetupStep { Welcome, Overlay, KeepAlive, Unblock, Accessibility, Restart, Done }

object SetupFlow {

    /** The steps with a progress dot, in order; Unblock only where it applies. */
    fun dotted(unblock: Boolean): List<SetupStep> = buildList {
        add(SetupStep.Overlay)
        add(SetupStep.KeepAlive)
        if (unblock) add(SetupStep.Unblock)
        add(SetupStep.Accessibility)
    }

    /**
     * The step to show now.
     *
     * [welcomed] is whether the user has pressed through the welcome screen
     * this time round; it exists only so the intro is not skipped for a fresh
     * install, and never pins the flow to something already granted.
     * [keepAlive] is true once battery and Autostart are handled -- or the
     * user chose to skip them. [unblock] is true while restricted settings
     * still need allowing.
     */
    fun nextStep(
        welcomed: Boolean,
        overlay: Boolean,
        keepAlive: Boolean,
        service: Boolean,
        running: Boolean,
        unblock: Boolean = false,
    ): SetupStep = when {
        service && running && overlay -> SetupStep.Done
        !welcomed && !(service && running) -> SetupStep.Welcome
        !overlay -> SetupStep.Overlay
        !keepAlive && !running -> SetupStep.KeepAlive
        // Once the switch is on, restricted settings no longer matter.
        unblock && !service -> SetupStep.Unblock
        !service -> SetupStep.Accessibility
        !running -> SetupStep.Restart
        else -> SetupStep.Done
    }

    /** Which dot the step sits on; the restart shares accessibility's. */
    fun dotIndex(step: SetupStep, unblock: Boolean): Int {
        val dots = dotted(unblock)
        return when (step) {
            SetupStep.Welcome -> -1
            SetupStep.Restart -> dots.indexOf(SetupStep.Accessibility)
            SetupStep.Done -> dots.size
            else -> dots.indexOf(step)
        }
    }

    /** "1 step left" / "2 steps left" / "You're all set." */
    fun statusLine(missing: Int): String = when (missing) {
        0 -> "You're all set."
        1 -> "1 step left"
        else -> "$missing steps left"
    }
}
