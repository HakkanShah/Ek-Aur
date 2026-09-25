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
    /** `viewIdResourceName`, e.g. `com.instagram.android:id/clips_viewer_view_pager`. */
    val viewId: String? = null,
    val contentDescription: String? = null,
    /** Net vertical movement reported by the event, when available. */
    val scrollDeltaY: Int = 0,
    /**
     * Net horizontal movement, when available (Android 9+). A scroll that moves
     * only sideways is a tab strip or a carousel, never a vertical feed of
     * short videos, whatever its shape says.
     */
    val scrollDeltaX: Int = 0,
    /**
     * First and last adapter positions visible after the scroll.
     *
     * These are the whole ballgame. A full-screen snapping pager shows exactly
     * one item, so `fromIndex == toIndex`; an ordinary list shows several, so
     * they differ. That difference separates the Reels player from the main feed
     * structurally, without depending on any name Instagram can rename.
     */
    val fromIndex: Int = NO_INDEX,
    val toIndex: Int = NO_INDEX,
) {
    enum class Kind {
        /** Foreground window changed -- used only to notice leaving the app. */
        WindowStateChanged,

        /** Content within the current window changed. No longer subscribed to. */
        WindowContentChanged,

        /** A scrollable view scrolled. The only event that moves the state machine. */
        ViewScrolled,
    }

    companion object {
        const val NO_INDEX = -1
    }
}

/** What a scroll event's shape says about the surface that produced it. */
enum class ScrollShape {
    /** Exactly one full-screen item visible -- a snapping pager, i.e. Reels or Shorts. */
    Player,

    /** Several items visible -- an ordinary list, i.e. the feed. Never counts. */
    List,

    /** No usable positions reported; shape unknown. */
    Unknown,
}
