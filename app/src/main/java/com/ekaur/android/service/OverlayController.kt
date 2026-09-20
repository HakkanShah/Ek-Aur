package com.ekaur.android.service

import android.content.Context
import com.ekaur.android.detect.DetectionState
import com.ekaur.android.overlay.MilestoneAnnouncer
import com.ekaur.android.overlay.OverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decides when the counter is on screen.
 *
 * Visible only while the player is, so the pill never sits over the rest of the
 * phone. All window work is bounced to the main thread, because `WindowManager`
 * requires it, and serialised behind a mutex so an add and a remove can never
 * interleave into a torn state.
 */
class OverlayController(
    context: Context,
    counts: StateFlow<Int>,
    private val scope: CoroutineScope,
    announcer: MilestoneAnnouncer,
) {

    private val host = OverlayHost(context, counts, announcer)
    private val windowLock = Mutex()

    @Volatile
    private var lastState: DetectionState? = null

    /** When the player was last seen, which is what stickiness is measured from. */
    @Volatile
    private var lastInReelsAtMs = 0L

    private var hideJob: Job? = null

    /** Whether the user has granted the draw-over-other-apps permission. */
    fun canDraw(): Boolean = host.canDrawOverlay()

    /**
     * Called on every event and on every tick, so it does nothing unless the
     * state actually changed -- otherwise this would launch a few coroutines a
     * second just to re-decide the same thing.
     */
    fun onDetectionState(state: DetectionState) {
        if (state == lastState) return
        lastState = state
        when (state) {
            DetectionState.InReels -> {
                lastInReelsAtMs = System.currentTimeMillis()
                show()
            }

            // Still inside Instagram. Comments, the feed and tab swipes all land
            // here, and none of them mean the user is done watching reels, so
            // the pill outlives them.
            DetectionState.InApp -> {
                val sinceReels = System.currentTimeMillis() - lastInReelsAtMs
                hideAfter((STICKY_AFTER_REELS_MS - sinceReels).coerceAtLeast(0))
            }

            // Left Instagram entirely -- but a notification or a glance at
            // another app should not tear the window down either.
            DetectionState.Idle -> hideAfter(IDLE_GRACE_MS)
        }
    }

    private fun show() {
        // Cancel any pending teardown: the player came back before the grace
        // period elapsed, so the window should simply stay where it is.
        hideJob?.cancel()
        hideJob = null
        if (!host.canDrawOverlay()) return
        scope.launch(Dispatchers.Main) {
            windowLock.withLock { host.show() }
        }
    }

    /**
     * Removing and re-adding a window is visible, so leaving the player waits
     * rather than tearing the pill down immediately. Showing stays instant;
     * only hiding is delayed.
     */
    private fun hideAfter(delayMs: Long) {
        hideJob?.cancel()
        hideJob = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            windowLock.withLock { host.hide() }
        }
    }

    /**
     * Tears the window down. **Must be called from the main thread**, which the
     * service's onUnbind/onDestroy callbacks already are.
     *
     * Deliberately synchronous: the caller cancels the scope immediately after,
     * so anything posted to it would be cancelled before it ran and the window
     * would be left on screen with no service behind it.
     */
    fun destroy() {
        lastState = null
        hideJob?.cancel()
        hideJob = null
        host.hide()
    }

    private companion object {
        /**
         * How long the pill survives inside Instagram after the player was last
         * seen. Generous on purpose: the detector's idea of "in reels" is tuned
         * for counting accuracy, and mirroring it exactly made the pill vanish
         * while the user was simply watching a video through.
         */
        const val STICKY_AFTER_REELS_MS = 20_000L

        /** Covers a notification, a quick app switch, or a glance at something else. */
        const val IDLE_GRACE_MS = 2_000L
    }
}
