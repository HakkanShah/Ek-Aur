package com.ekaur.android.overlay

import com.ekaur.android.copy.SarcasmCatalogue
import com.ekaur.android.data.repo.DayClock
import com.ekaur.android.milestone.MilestoneEngine
import com.ekaur.android.milestone.MilestoneLog
import com.ekaur.android.milestone.Progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Decides when the pill has something to say, and for how long.
 *
 * Two things can produce a line. A [MilestoneEngine] milestone -- a round
 * number, a long sitting, the small hours -- fires at most once a day and wins
 * when it happens. Otherwise the recurring every-N-reels line keeps the pill
 * talking, and repeats freely.
 *
 * Lives outside the composable on purpose. Held in `remember`, the message and
 * the milestone bookkeeping died with the overlay window every time it hid --
 * which cut announcements off part-way and let the same milestone fire twice.
 * Owned by the service, it survives the window coming and going.
 */
class MilestoneAnnouncer(
    scope: CoroutineScope,
    counts: StateFlow<Int>,
    private val log: MilestoneLog = MilestoneLog.None,
    private val engine: MilestoneEngine = MilestoneEngine(),
    private val clock: DayClock = DayClock(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var announcement: Job? = null

    /**
     * When the current sitting began, or null between sittings.
     *
     * Written from the service's event handling and read from the collector
     * below, which are separate coroutines, so it is not left to chance.
     */
    @Volatile
    private var sessionStartedAtMs: Long? = null

    /**
     * Whether a reel has actually been seen since the service started.
     *
     * The count flow is seeded with zero and then replaced by whatever is
     * already stored for today, so starting the service on a day with 90 reels
     * behind it looks exactly like scrolling 90 reels in an instant. Announcing
     * that would be wrong, and would burn a once-a-day milestone on a number
     * the user reached hours ago. Nothing is announced until detection has
     * reported a reel of its own.
     */
    @Volatile
    private var seenAReel = false

    init {
        scope.launch {
            var before: Progress? = null
            counts.collect { count ->
                val at = nowMs()
                val now = progressAt(count, at)
                val previous = before
                // Tracked even while nothing may be announced, so the first
                // real reel is measured against the stored total rather than
                // against zero.
                before = now

                if (previous == null || !seenAReel) return@collect
                if (count <= previous.reels) return@collect

                val line = lineFor(now, previous, at) ?: return@collect
                announce(line, scope)
            }
        }
    }

    /**
     * Told by the service the moment detection counts a reel.
     *
     * Arrives before the database write that moves the count flow, so by the
     * time the new total lands this is already true.
     */
    fun onReelCounted() {
        seenAReel = true
    }

    /** Told by the service, so a sitting's length is known without polling. */
    fun onSessionStarted(atMs: Long) {
        sessionStartedAtMs = atMs
    }

    fun onSessionEnded() {
        sessionStartedAtMs = null
    }

    /**
     * The line this reel earns, or null for silence.
     *
     * A failed database read must not cost the user their line, so a milestone
     * lookup that throws falls through to the recurring copy rather than
     * propagating -- worst case a milestone repeats, which is invisible next to
     * the pill going quiet.
     */
    private suspend fun lineFor(now: Progress, before: Progress, atMs: Long): String? {
        val date = clock.dateOf(atMs)
        val fired = runCatching { log.firedOn(date) }.getOrDefault(emptySet())
        val outcome = engine.evaluate(now, before, fired)

        if (outcome != null) {
            // Every spent id is recorded, not just the announced one, or the
            // ones it superseded return on the next reel. Recorded before the
            // line is shown: a crash between the two should cost the line, not
            // let the milestone fire over and over for the rest of the day.
            for (id in outcome.spent) {
                runCatching { log.markFired(date, id, atMs) }
            }
            val milestone = outcome.announce
            return SarcasmCatalogue.lineForKey(milestone.copyKey, now.reels, hourAt(atMs))
        }

        // Fires on crossing a multiple rather than landing on one -- two reels
        // can arrive in a single update.
        val every = SarcasmCatalogue.MILESTONE_EVERY
        if (now.reels / every <= before.reels / every) return null
        return SarcasmCatalogue.lineFor(now.reels, hourAt(atMs))
    }

    private fun progressAt(count: Int, atMs: Long): Progress {
        val started = sessionStartedAtMs
        val sessionMinutes = if (started == null) 0 else ((atMs - started) / 60_000L).toInt()
        val local = Instant.ofEpochMilli(atMs).atZone(zone)
        return Progress(
            reels = count,
            sessionMinutes = sessionMinutes.coerceAtLeast(0),
            minuteOfDay = local.hour * 60 + local.minute,
        )
    }

    private fun hourAt(atMs: Long): Int =
        Instant.ofEpochMilli(atMs).atZone(zone).hour

    private fun announce(line: String, scope: CoroutineScope) {
        announcement?.cancel()
        announcement = scope.launch {
            _message.value = line
            delay(MESSAGE_DURATION_MS)
            _message.value = null
        }
    }

    companion object {
        /**
         * Long enough that the line is comfortably readable rather than
         * glimpsed -- the previous 3.6s included the expand animation, leaving
         * well under two seconds of settled text.
         */
        const val MESSAGE_DURATION_MS = 4_000L
    }
}
