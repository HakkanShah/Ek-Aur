package com.ekaur.android.detect

/**
 * A normalized accessibility event, with every Android type stripped off.
 *
 * The service translates `AccessibilityEvent` into this at the boundary so that
 * everything below it is plain Kotlin and runs on the JVM. Detection is the part
 * of this app most likely to break when Instagram ships a new build, so it is
 * deliberately the part that is cheapest to test.
 *
 * All timing is carried on the signal itself rather than read from a clock, so
 * tests are deterministic and replayable.
 */
data class ScrollSignal(
    val packageName: String,
    val kind: Kind,
    val timestampMs: Long,
    /** Fully-qualified view/activity class, when the event carries one. */
    val className: String? = null,
    /** `viewIdResourceName`, e.g. `com.instagram.android:id/clips_viewer_root`. */
    val viewId: String? = null,
    val contentDescription: String? = null,
    /** Net vertical movement reported by the event, when available. */
    val scrollDeltaY: Int = 0,
    /**
     * Adapter position the scroll landed on. Instagram's Reels player is a
     * pager, so when this is present it is by far the most reliable "moved to
     * the next video" signal. [NO_INDEX] when the event carries none.
     */
    val itemIndex: Int = NO_INDEX,
) {
    enum class Kind {
        /** Foreground window changed -- used to enter/leave the player. */
        WindowStateChanged,

        /** Content within the current window changed. */
        WindowContentChanged,

        /** A scrollable view scrolled. The event that actually does the counting. */
        ViewScrolled,
    }

    companion object {
        const val NO_INDEX = -1
    }
}
