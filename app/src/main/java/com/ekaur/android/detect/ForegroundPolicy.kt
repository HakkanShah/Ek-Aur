package com.ekaur.android.detect

/**
 * Decides whether a window event from another package really means the user
 * left the app being tracked.
 *
 * It usually does not. A heads-up notification is a window owned by the
 * notifying app or by the system UI, and it fires the same
 * `TYPE_WINDOW_STATE_CHANGED` as an app switch. Believing it tore the overlay
 * down and -- far worse -- ended the session and cleared the counting
 * baselines, so the next reel after every notification went uncounted.
 */
object ForegroundPolicy {

    /**
     * Packages that own windows but never represent the foreground app: the
     * system UI (notification shade, heads-up banners, volume panel) and the
     * platform itself (toasts, system dialogs).
     */
    private val NEVER_FOREGROUND = setOf(
        "com.android.systemui",
        "android",
    )

    /**
     * @param eventPackage the package on the window event.
     * @param actualForeground what is genuinely in front, or null if unknown.
     * @param isTracked whether a package is one the app counts.
     *
     * Ambiguity resolves to "stayed": a wrong "left" destroys a count and hides
     * the overlay, while a wrong "stayed" only delays a session ending.
     */
    fun hasLeftTrackedApp(
        eventPackage: String,
        actualForeground: String?,
        isTracked: (String) -> Boolean,
    ): Boolean {
        if (isTracked(eventPackage)) return false
        if (eventPackage in NEVER_FOREGROUND) return false

        // Nothing to check against -- assume the user is where they were.
        if (actualForeground == null) return false

        // An overlay window appeared, but the app underneath is still in front.
        if (isTracked(actualForeground)) return false

        return true
    }
}
