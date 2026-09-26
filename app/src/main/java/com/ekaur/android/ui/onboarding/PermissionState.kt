package com.ekaur.android.ui.onboarding

import android.content.Context
import com.ekaur.android.service.KeepAlive
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.setup.RestrictedSetting
import com.ekaur.android.setup.Verdict

/**
 * Every grant the app can ask for, read in one go so no two screens disagree.
 *
 * "Switched on" and "running" are separate on purpose. Setup used to call
 * the app ready the moment the accessibility switch was on, while the row
 * below it said the service wasn't running -- on Xiaomi phones the switch can
 * be on while the phone refuses to start the app. Nothing is "all set" now
 * unless Android actually started it.
 */
data class PermissionState(
    /** The accessibility switch is on (it may not have started yet). */
    val service: Boolean = false,
    /** Android actually started the service. */
    val running: Boolean = false,
    val overlay: Boolean = false,
    val battery: Boolean = false,
    val usage: Boolean = false,
    /** Autostart as the phone reports it; null where it can't be read. */
    val autostart: Boolean? = null,
    /** This phone has an Autostart screen at all. */
    val autostartScreen: Boolean = false,
    /** The user came back from the Autostart screen (for phones that can't be read). */
    val autostartConfirmed: Boolean = false,
    /**
     * "Allow restricted settings" still stands between the user and the
     * accessibility switch (Android 13+, installed from a file, not yet
     * allowed -- or not confirmed, where the phone won't say).
     */
    val unblockNeeded: Boolean = false,
) {
    /** Counting works and shows: started, and allowed to draw the pill. */
    val allGranted: Boolean get() = service && running && overlay

    /** The switch is on but the phone never started the service. */
    val notRunning: Boolean get() = service && !running

    /** Autostart is taken care of, as far as anyone can tell. */
    val autostartDone: Boolean get() = !autostartScreen || (autostart ?: autostartConfirmed)

    /** What keeps the phone from stopping the counter: battery, and Autostart where it exists. */
    val keepAliveDone: Boolean get() = battery && autostartDone

    val recommendedDone: Boolean get() = keepAliveDone && usage

    val requiredMissing: Int get() = listOf(service && running, overlay).count { !it }

    companion object {
        /**
         * The user's "I allowed it" only counts where the phone can't say.
         * When Android reports the gate as still shut, Android is believed.
         */
        fun unblockNeeded(verdict: Verdict, confirmed: Boolean): Boolean = when {
            !RestrictedSetting.needsUnblockStep(verdict) -> false
            verdict == Verdict.Restricted -> true
            else -> !confirmed
        }

        /** There is no callback for any of these, so callers re-read on resume. */
        fun read(
            context: Context,
            autostartConfirmed: Boolean = false,
            unblockConfirmed: Boolean = false,
        ): PermissionState {
            val running = ServiceControl.isAccessibilityServiceRunning(context)
            val verdict = ServiceControl.restrictedVerdict(context)
            return PermissionState(
                service = running || ServiceControl.isAccessibilityServiceEnabled(context),
                running = running,
                overlay = ServiceControl.canDrawOverlay(context),
                battery = ServiceControl.isIgnoringBatteryOptimisations(context),
                usage = ServiceControl.hasUsageAccess(context),
                autostart = KeepAlive.autostart(context),
                autostartScreen = KeepAlive.hasAutostartScreen(),
                autostartConfirmed = autostartConfirmed,
                unblockNeeded = unblockNeeded(verdict, unblockConfirmed),
            )
        }
    }
}
