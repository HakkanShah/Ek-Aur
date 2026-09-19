package com.ekaur.android.service

import android.content.Context
import com.ekaur.android.detect.DetectionState
import com.ekaur.android.overlay.OverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Decides when the counter is on screen.
 *
 * Visible only while the player is, so the pill never sits over the rest of the
 * phone. All window work is bounced to the main thread: `WindowManager` requires
 * it, and detection runs on a background dispatcher.
 */
class OverlayController(
    context: Context,
    counts: StateFlow<Int>,
    private val scope: CoroutineScope,
) {

    private val host = OverlayHost(context, counts)

    @Volatile
    private var lastState: DetectionState? = null

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
            DetectionState.InReels -> showIfAllowed()
            DetectionState.InApp, DetectionState.Idle -> hide()
        }
    }

    fun showIfAllowed() {
        if (!host.canDrawOverlay()) return
        scope.launch(Dispatchers.Main) { host.show() }
    }

    fun hide() {
        if (!host.isShowing) return
        scope.launch(Dispatchers.Main) { host.hide() }
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
        host.hide()
    }
}
