package com.ekaur.android.detect

/**
 * Turns a stream of [ScrollSignal]s into "a reel was scrolled".
 *
 * Rewritten after a device dump exposed two problems with the first version.
 *
 * **Only scroll events move the state machine.** Window and content events used
 * to flip the detector in and out of the player based on string matches, and
 * they flapped constantly -- Instagram's Reels screen carries words that read as
 * not-the-player, and its feed carries a tab described as "Reels". Every flip
 * reset the counting baseline, so most swipes were swallowed. Nothing but a
 * scroll can change the state now.
 *
 * **Baselines are kept per view.** A scroll in the feed must not clobber the
 * position the player was last seen at, so last-known positions are held in a
 * map keyed by view, and cleared only on leaving the app or after a long idle.
 *
 * Counting itself:
 * 1. **Position-based**, whenever the event reports adapter positions. Reels is a
 *    snapping pager, so an advance in position is exactly one swipe regardless of
 *    fling speed.
 * 2. **Settle-window fallback**, only when no positions are reported at all. A
 *    swipe fires many scroll events, so they are accumulated and counted once
 *    activity goes quiet. Trades undercounting very rapid swipes for never
 *    overcounting one slow one.
 *
 * Holds no clock of its own. Time arrives on signals, or via [onTick], so tests
 * are fully deterministic.
 */
class ReelDetector(
    private val rulesFor: (String) -> AppRules? = DetectorRules::forPackage,
) {

    /**
     * Driven from the accessibility callback on the main thread and from the
     * tick coroutine on a background dispatcher, so every entry point below is
     * synchronised and this field is volatile. Without that, concurrent writes
     * corrupted [lastIndexByView] -- dropping entries so advances read as first
     * sightings and went uncounted -- and torn reads of [lastPlayerScrollMs]
     * flipped the state mid-scrolling, which made the overlay blink.
     */
    @Volatile
    var state: DetectionState = DetectionState.Idle
        private set

    private var rules: AppRules? = null
    private var activePackage: String? = null

    // Session
    private var sessionActive = false
    private var sessionStartMs = 0L
    private var sessionCount = 0
    private var lastReelsActivityMs = 0L

    /** When a scroll last came from the player itself. Drives the exit grace. */
    private var lastPlayerScrollMs = 0L

    // Fallback burst accumulator
    private var burstOpen = false
    private var burstNet = 0
    private var burstLastMs = 0L
    private var burstSettleWindow = 300L
    private var burstMinScroll = 24

    /** Last seen adapter position, per scrolling view. Feed and player never mix. */
    private val lastIndexByView = mutableMapOf<String, Int>()

    @Synchronized
    fun onSignal(signal: ScrollSignal): DetectionResult {
        val events = mutableListOf<DetectionEvent>()

        // A pending burst may have settled while we were waiting for this signal.
        flushBurst(signal.timestampMs, events)

        val signalRules = rulesFor(signal.packageName)
        if (signalRules == null) {
            // Foreground moved to an app we do not track.
            leaveApp(signal.timestampMs, events)
            return DetectionResult(events, state)
        }

        if (activePackage != signal.packageName) {
            leaveApp(signal.timestampMs, events)
            activePackage = signal.packageName
            rules = signalRules
        }

        if (state == DetectionState.Idle) state = DetectionState.InApp

        // Everything below is scroll-driven. Window and content events are
        // deliberately inert: letting them change state is what broke the first
        // version.
        if (signal.kind != ScrollSignal.Kind.ViewScrolled) return DetectionResult(events, state)

        when (signalRules.shapeOf(signal)) {
            ScrollShape.Player -> {
                state = DetectionState.InReels
                lastPlayerScrollMs = signal.timestampMs
                lastReelsActivityMs = signal.timestampMs
                startSessionIfNeeded(signal.timestampMs, events)
                // A view can be known to be the player by id while this
                // particular event carries no position; then only the timing
                // fallback is available.
                if (signal.fromIndex >= 0) {
                    countByPosition(signal, events)
                } else {
                    accumulateBurst(signal, signalRules)
                }
            }

            ScrollShape.List -> {
                // Several items visible: an ordinary list, never counts.
                //
                // But Instagram scrolls a background list while Reels is open,
                // so this alone does not mean the player is gone -- only a list
                // scroll with no recent player activity does.
                val quietFor = signal.timestampMs - lastPlayerScrollMs
                if (state == DetectionState.InReels && quietFor >= signalRules.playerExitGraceMs) {
                    state = DetectionState.InApp
                }
                // Any pending burst belonged to the player, not to this list.
                burstOpen = false
                burstNet = 0
            }

            ScrollShape.Unknown -> {
                // No positions reported. Only meaningful if we already believe
                // the player is on screen.
                if (state == DetectionState.InReels) {
                    lastReelsActivityMs = signal.timestampMs
                    accumulateBurst(signal, signalRules)
                }
            }
        }

        return DetectionResult(events, state)
    }

    /**
     * Drives time-based transitions that no incoming signal would trigger: a
     * burst settling, or a session going cold. The service calls this on a
     * short timer.
     */
    @Synchronized
    fun onTick(nowMs: Long): DetectionResult {
        val events = mutableListOf<DetectionEvent>()
        flushBurst(nowMs, events)

        val activeRules = rules ?: return DetectionResult(events, state)

        // Leaving Reels without scrolling anything else produces no further
        // signal, so the state is closed out on time instead.
        if (state == DetectionState.InReels &&
            nowMs - lastPlayerScrollMs >= activeRules.playerExitGraceMs * IDLE_EXIT_FACTOR
        ) {
            state = DetectionState.InApp
        }

        val gap = activeRules.sessionGapMs
        if (sessionActive && nowMs - lastReelsActivityMs >= gap) {
            endSession(nowMs, events)
            // A cold session means the user moved on; stale positions would
            // produce a bogus jump if they come back to a different reel.
            lastIndexByView.clear()
        }
        return DetectionResult(events, state)
    }

    /** Drops all state. Used when the service disconnects. */
    @Synchronized
    fun reset() {
        state = DetectionState.Idle
        rules = null
        activePackage = null
        sessionActive = false
        sessionStartMs = 0
        sessionCount = 0
        lastReelsActivityMs = 0
        lastPlayerScrollMs = 0
        burstOpen = false
        burstNet = 0
        burstLastMs = 0
        lastIndexByView.clear()
    }

    // --- counting ---------------------------------------------------------

    private fun countByPosition(signal: ScrollSignal, events: MutableList<DetectionEvent>) {
        // A position-carrying event supersedes anything the fallback accumulated.
        burstOpen = false
        burstNet = 0

        val key = signal.viewKey()
        val previous = lastIndexByView.put(key, signal.fromIndex)

        // First sighting of this view establishes the baseline. Costs at most one
        // count per session, and only for the very first swipe seen.
        if (previous == null) return

        val delta = signal.fromIndex - previous
        if (delta <= 0) return  // scrolled back up to rewatch, or no movement

        // A snapping pager advances one item per swipe; anything larger is noise
        // or a jump, so cap it rather than inventing counts.
        repeat(minOf(delta, MAX_ITEMS_PER_SIGNAL)) {
            emitReel(signal.timestampMs, events)
        }
    }

    private fun accumulateBurst(signal: ScrollSignal, appRules: AppRules) {
        if (signal.scrollDeltaY == 0) return
        burstOpen = true
        burstNet += signal.scrollDeltaY
        burstLastMs = signal.timestampMs
        burstSettleWindow = appRules.settleWindowMs
        burstMinScroll = appRules.minNetScroll
    }

    private fun flushBurst(
        nowMs: Long,
        events: MutableList<DetectionEvent>,
        force: Boolean = false,
    ) {
        if (!burstOpen) return
        if (!force && nowMs - burstLastMs < burstSettleWindow) return

        val net = burstNet
        burstOpen = false
        burstNet = 0

        // Only forward movement counts; scrolling back up is not a new reel.
        if (net >= burstMinScroll) emitReel(burstLastMs, events)
    }

    private fun emitReel(timestampMs: Long, events: MutableList<DetectionEvent>) {
        val pkg = activePackage ?: return
        startSessionIfNeeded(timestampMs, events)
        sessionCount++
        lastReelsActivityMs = timestampMs
        events += DetectionEvent.ReelScrolled(pkg, timestampMs)
    }

    // --- lifecycle --------------------------------------------------------

    private fun leaveApp(nowMs: Long, events: MutableList<DetectionEvent>) {
        // Backgrounding is itself a settle: a swipe finished just before the
        // user left still happened, so flush it rather than dropping it.
        flushBurst(nowMs, events, force = true)
        if (sessionActive) endSession(nowMs, events)
        state = DetectionState.Idle
        activePackage = null
        rules = null
        lastIndexByView.clear()
        burstOpen = false
        burstNet = 0
    }

    private fun startSessionIfNeeded(nowMs: Long, events: MutableList<DetectionEvent>) {
        if (sessionActive) return
        val pkg = activePackage ?: return
        sessionActive = true
        sessionStartMs = nowMs
        sessionCount = 0
        events += DetectionEvent.SessionStarted(pkg, nowMs)
    }

    private fun endSession(nowMs: Long, events: MutableList<DetectionEvent>) {
        if (!sessionActive) return
        val pkg = activePackage
        sessionActive = false
        if (pkg != null) {
            events += DetectionEvent.SessionEnded(
                packageName = pkg,
                startedAtMs = sessionStartMs,
                endedAtMs = maxOf(nowMs, lastReelsActivityMs),
                reelCount = sessionCount,
            )
        }
        sessionCount = 0
    }

    private fun ScrollSignal.viewKey(): String = viewId ?: className ?: "unknown"

    private companion object {
        const val MAX_ITEMS_PER_SIGNAL = 3

        /**
         * Sitting on one reel watching it is normal, so going idle needs a much
         * longer silence than a competing list scroll does before the player is
         * considered closed.
         */
        const val IDLE_EXIT_FACTOR = 8
    }
}
