package com.ekaur.android.detect


/**
 * Turns a stream of [ScrollSignal]s into "a reel was scrolled".
 *
 * Two counting strategies, in priority order:
 *
 * 1. **Index-based.** Instagram's Reels player is a snapping pager, so when an
 *    event carries an adapter position, a change in that position *is* the
 *    swipe. Exact, and immune to how fast the user flings.
 *
 * 2. **Settle-window fallback.** When no index is available, scroll events are
 *    accumulated into a burst and counted as one swipe once activity goes quiet
 *    for [AppRules.settleWindowMs]. A single swipe fires many scroll events, so
 *    counting them raw would wildly over-count.
 *
 *    Known limitation: two swipes closer together than the settle window merge
 *    into one count. That is the price of not over-counting, and it only applies
 *    when Instagram stops reporting indices -- the accurate path handles the
 *    normal case.
 *
 * Holds no clock of its own. Time arrives on signals, or via [onTick], so tests
 * are fully deterministic.
 */
class ReelDetector(
    private val rulesFor: (String) -> AppRules? = DetectorRules::forPackage,
) {

    var state: DetectionState = DetectionState.Idle
        private set

    private var rules: AppRules? = null
    private var activePackage: String? = null

    // Session
    private var sessionActive = false
    private var sessionStartMs = 0L
    private var sessionCount = 0
    private var lastReelsActivityMs = 0L

    // Fallback burst accumulator
    private var burstOpen = false
    private var burstNet = 0
    private var burstLastMs = 0L

    // Index tracking
    private var lastIndex = ScrollSignal.NO_INDEX

    fun onSignal(signal: ScrollSignal): List<DetectionEvent> {
        val events = mutableListOf<DetectionEvent>()

        // A pending burst may have settled while we were waiting for this signal.
        flushBurst(signal.timestampMs, events)

        val signalRules = rulesFor(signal.packageName)
        if (signalRules == null) {
            // Foreground moved to an app we do not track.
            leaveApp(signal.timestampMs, events)
            return events
        }

        if (activePackage != signal.packageName) {
            leaveApp(signal.timestampMs, events)
            activePackage = signal.packageName
            rules = signalRules
        }

        // A tracked app is in the foreground, so we are at least in-app even if
        // this signal says nothing about the player.
        if (state == DetectionState.Idle) state = DetectionState.InApp

        val playerVisible = evaluatePlayerVisible(signal, signalRules)
        if (playerVisible != null) {
            applyPlayerVisibility(playerVisible, signal.timestampMs, events)
        }

        if (state == DetectionState.InReels && signal.kind == ScrollSignal.Kind.ViewScrolled) {
            lastReelsActivityMs = signal.timestampMs
            countScroll(signal, signalRules, events)
        }

        return events
    }

    /**
     * Drives time-based transitions that no incoming signal would trigger: a
     * burst settling, or a session going cold. The service calls this on a
     * short timer while the player is on screen.
     */
    fun onTick(nowMs: Long): List<DetectionEvent> {
        val events = mutableListOf<DetectionEvent>()
        flushBurst(nowMs, events)

        val gap = rules?.sessionGapMs ?: return events
        if (sessionActive && nowMs - lastReelsActivityMs >= gap) {
            endSession(nowMs, events)
        }
        return events
    }

    /** Drops all state. Used when the service disconnects. */
    fun reset() {
        state = DetectionState.Idle
        rules = null
        activePackage = null
        sessionActive = false
        sessionStartMs = 0
        sessionCount = 0
        lastReelsActivityMs = 0
        burstOpen = false
        burstNet = 0
        burstLastMs = 0
        lastIndex = ScrollSignal.NO_INDEX
    }

    // --- visibility -------------------------------------------------------

    /**
     * Returns true/false when this signal says something about whether the
     * player is on screen, or null when it says nothing and the current
     * assessment should stand.
     */
    private fun evaluatePlayerVisible(signal: ScrollSignal, appRules: AppRules): Boolean? {
        val haystack = listOfNotNull(
            signal.className,
            signal.viewId,
            signal.contentDescription,
        ).joinToString(" ").lowercase()

        if (haystack.isBlank()) return null

        // Negative hints win: a screen that looks like both is not the player.
        if (appRules.notPlayerHints.any { haystack.contains(it.lowercase()) }) return false

        val positive = appRules.playerClassHints.any { haystack.contains(it.lowercase()) } ||
            appRules.playerViewIdHints.any { haystack.contains(it.lowercase()) } ||
            appRules.playerContentHints.any { haystack.contains(it.lowercase()) }

        if (positive) return true

        // Only a window change is trusted to say "the player is gone". A content
        // change elsewhere on the same screen should not knock us out of Reels.
        return if (signal.kind == ScrollSignal.Kind.WindowStateChanged) false else null
    }

    private fun applyPlayerVisibility(
        visible: Boolean,
        nowMs: Long,
        events: MutableList<DetectionEvent>,
    ) {
        if (visible) {
            if (state != DetectionState.InReels) {
                state = DetectionState.InReels
                lastIndex = ScrollSignal.NO_INDEX
                lastReelsActivityMs = nowMs
                startSessionIfNeeded(nowMs, events)
            }
        } else {
            if (state == DetectionState.InReels) {
                // Leaving the player does not end the session immediately -- a
                // detour into comments and straight back is still one sitting.
                // onTick closes it once the gap elapses.
                state = DetectionState.InApp
                lastIndex = ScrollSignal.NO_INDEX
            } else if (state == DetectionState.Idle) {
                state = DetectionState.InApp
            }
        }
    }

    private fun leaveApp(nowMs: Long, events: MutableList<DetectionEvent>) {
        // Backgrounding is itself a settle: a swipe finished just before the
        // user left still happened, so flush it rather than dropping it.
        flushBurst(nowMs, events, force = true)
        if (sessionActive) endSession(nowMs, events)
        state = DetectionState.Idle
        activePackage = null
        rules = null
        lastIndex = ScrollSignal.NO_INDEX
        burstOpen = false
        burstNet = 0
    }

    // --- counting ---------------------------------------------------------

    private fun countScroll(
        signal: ScrollSignal,
        appRules: AppRules,
        events: MutableList<DetectionEvent>,
    ) {
        if (signal.itemIndex != ScrollSignal.NO_INDEX) {
            // Accurate path. Any pending fallback burst is now redundant.
            burstOpen = false
            burstNet = 0

            val previous = lastIndex
            lastIndex = signal.itemIndex

            if (previous == ScrollSignal.NO_INDEX) return          // first sighting, nothing to diff
            val delta = signal.itemIndex - previous
            if (delta <= 0) return                    // scrolled back up, or no movement

            // A snapping pager moves one item per swipe; anything larger is
            // noise or a jump, so cap it rather than inventing counts.
            repeat(minOf(delta, MAX_ITEMS_PER_SIGNAL)) {
                emitReel(signal.timestampMs, events)
            }
            return
        }

        // Fallback path: accumulate until things go quiet.
        if (signal.scrollDeltaY == 0) return
        burstOpen = true
        burstNet += signal.scrollDeltaY
        burstLastMs = signal.timestampMs
        burstSettleWindow = appRules.settleWindowMs
        burstMinScroll = appRules.minNetScroll
        burstPackage = signal.packageName
    }

    private var burstSettleWindow = 300L
    private var burstMinScroll = 24
    private var burstPackage: String? = null

    private fun flushBurst(
        nowMs: Long,
        events: MutableList<DetectionEvent>,
        force: Boolean = false,
    ) {
        if (!burstOpen) return
        if (!force && nowMs - burstLastMs < burstSettleWindow) return

        val net = burstNet
        val pkg = burstPackage
        burstOpen = false
        burstNet = 0

        // Only forward movement counts; scrolling back up to rewatch is not a
        // new reel.
        if (pkg != null && net >= burstMinScroll) {
            emitReel(burstLastMs, events)
        }
    }

    private fun emitReel(timestampMs: Long, events: MutableList<DetectionEvent>) {
        val pkg = activePackage ?: burstPackage ?: return
        startSessionIfNeeded(timestampMs, events)
        sessionCount++
        lastReelsActivityMs = timestampMs
        events += DetectionEvent.ReelScrolled(pkg, timestampMs)
    }

    // --- sessions ---------------------------------------------------------

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
        val pkg = activePackage ?: burstPackage
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

    private companion object {
        const val MAX_ITEMS_PER_SIGNAL = 3
    }
}
