package com.ekaur.android.ui.onboarding

import android.content.Context
import com.ekaur.android.service.ServiceControl

/**
 * Every grant the app can ask for, read in one go so no two screens disagree.
 *
 * Two are required -- nothing counts without accessibility, and nothing shows
 * without the overlay. The other two only keep it alive on phones that kill
 * background work, so they are recommended, and never block "all set".
 */
data class PermissionState(
    val service: Boolean = false,
    val overlay: Boolean = false,
    val battery: Boolean = false,
    val usage: Boolean = false,
) {
    /** The two without which the app does nothing. */
    val allGranted: Boolean get() = service && overlay

    val recommendedDone: Boolean get() = battery && usage

    val requiredMissing: Int get() = listOf(service, overlay).count { !it }

    companion object {
        /** There is no callback for any of these, so callers re-read on resume. */
        fun read(context: Context): PermissionState = PermissionState(
            service = ServiceControl.isAccessibilityServiceEnabled(context),
            overlay = ServiceControl.canDrawOverlay(context),
            battery = ServiceControl.isIgnoringBatteryOptimisations(context),
            usage = ServiceControl.hasUsageAccess(context),
        )
    }
}
