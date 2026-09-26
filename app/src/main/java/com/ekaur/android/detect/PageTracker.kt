package com.ekaur.android.detect

import kotlin.math.abs

/**
 * Counts page flips for one app whose pager reports no positions (YouTube
 * Shorts), from scroll distances alone. The rules for a single settled
 * burst live in [PageFlip]; this holds everything that spans bursts.
 *
 * Hardened after a second phone (build 52) recognised the Shorts screen but
 * never counted a swipe. The server showed its database never held a single
 * Short, so every settled burst there must have missed the one-page test.
 * Three ways that happens, each handled here:
 *
 * - **The pager isn't the class we expected.** Movement is kept per view
 *   class and every class is judged, so whichever view moved exactly a page
 *   is the pager -- a RecyclerView on the realme, anything on another build.
 * - **A swipe arrives in pieces.** A burst that stops between pages on the
 *   Shorts screen is carried into the next one for a few seconds, so
 *   1300 + 1000 is judged as 2300.
 * - **The page isn't the size we guessed from the screen.** Two consecutive
 *   bursts of the same view moving the same distance prove the page size,
 *   whatever the screen height says.
 *
 * And one way a burst could never be judged at all: a small ticker or
 * carousel scrolling every 200ms kept pushing the settle timer. Only a
 * paging-sized move (or a view already known to be the pager) holds a burst
 * open now, and no burst lasts longer than [MAX_BURST_MS].
 */
class PageTracker(
    private val screenHeight: () -> Int,
    private val preferredClassHints: List<String> = emptyList(),
    private val settleWindowMs: Long = 300,
    /** A page height remembered from an earlier run, if any. */
    initialPageHeight: Int? = null,
    /** Told whenever the page height is learned, so it can be remembered. */
    private val onLearned: ((Int) -> Unit)? = null,
) {

    /** What a settled burst came to. */
    data class Result(
        /** Pages moved forward: counts to emit. */
        val flips: Int,
        /** Two or more views moved together -- the Shorts screen's signature. */
        val echo: Boolean,
        /** A line for the event log saying why. */
        val trace: String,
        /** When the burst's last move happened; counts are stamped with it. */
        val atMs: Long = 0L,
        /** The biggest distance any one view moved: tells a real scroll from a ticker. */
        val moved: Int = 0,
    )

    /** The page height, once proven. Survives leaving the app. */
    var pageHeight: Int? = initialPageHeight
        private set(value) {
            if (value != null && value != field) onLearned?.invoke(value)
            field = value
        }

    // Views that have moved by whole pages: the pager, however it reports itself.
    private val pagerClasses = mutableSetOf<String>()

    // The burst being gathered.
    private var open = false
    private var startMs = 0L
    private var lastMs = 0L
    private val nets = linkedMapOf<String, Int>()

    // A swipe that stopped between pages, waiting for the rest of it.
    private var carry = 0
    private var carryClass: String? = null
    private var carryAtMs = 0L

    // The previous big move, for proving the page size by repetition.
    private var lastCandidate: Pair<String, Int>? = null

    // Pages the user has swiped back from the furthest Short they reached.
    // Swiping forward again over those is a rewatch, not a new Short.
    private var behind = 0

    val isOpen: Boolean get() = open

    /** Adds one scroll. [cls] is the event's class name. */
    fun onScroll(cls: String, dy: Int, atMs: Long) {
        if (dy == 0) return
        if (!open) {
            open = true
            startMs = atMs
            lastMs = atMs
            nets.clear()
        }
        nets[cls] = (nets[cls] ?: 0) + dy
        if (holdsOpen(cls, dy)) lastMs = atMs
    }

    /** Judges the burst if it has settled (or [force]); null if nothing to judge yet. */
    fun settleIfDue(nowMs: Long, force: Boolean = false): Result? {
        if (!open) return null
        val quiet = nowMs - lastMs >= settleWindowMs
        val tooLong = nowMs - startMs >= MAX_BURST_MS
        if (!force && !quiet && !tooLong) return null
        open = false
        val moved = nets.values.maxOfOrNull { abs(it) } ?: 0
        return judge(nowMs).copy(atMs = lastMs, moved = moved)
    }

    /** Forget the burst and any carry, keeping what was learned. Leaving the app. */
    fun resetTransient() {
        open = false
        nets.clear()
        carry = 0
        carryClass = null
        lastCandidate = null
        behind = 0
    }

    /** The Shorts screen is gone (a list scrolled, or a long gap): a new feed next time. */
    fun leftPlayer() {
        behind = 0
    }

    /**
     * A settled burst, with rewatches taken out: pages swiped back are
     * remembered, and forward pages over them count nothing until the user is
     * past where they had been.
     */
    private fun judge(nowMs: Long): Result {
        val result = judgeMove(nowMs)
        if (result.flips == 0) {
            val back = pagesBack(result.echo)
            if (back == 0) return result
            behind = minOf(behind + back, MAX_BEHIND)
            return result.copy(trace = result.trace + " back=$back behind=$behind")
        }
        if (behind == 0) return result
        val rewatched = minOf(behind, result.flips)
        behind -= rewatched
        return result.copy(flips = result.flips - rewatched, trace = result.trace + ", rewatch=$rewatched so +${result.flips - rewatched} behind=$behind")
    }

    /**
     * Whole pages this burst moved backwards, or 0. Needs the page size and
     * the Shorts screen's echo: a home feed scrolled up by chance the length
     * of a page must never stop later Shorts from counting.
     */
    private fun pagesBack(echo: Boolean): Int {
        val page = pageHeight ?: return 0
        if (!echo) return 0
        val net = nets.entries
            .filter { it.value < 0 && (pagerClasses.isEmpty() || it.key in pagerClasses) }
            .minOfOrNull { it.value } ?: return 0
        val pages = Math.round(-net.toFloat() / page)
        val off = abs(-net - pages * page)
        return if (pages in 1..PageFlip.MAX_FLIPS_PER_BURST && off <= maxOf(24, (pages * page * 0.03f).toInt())) pages else 0
    }

    private fun holdsOpen(cls: String, dy: Int): Boolean =
        if (pagerClasses.isNotEmpty()) cls in pagerClasses else abs(dy) >= HOLD_MIN_DELTA

    private fun judgeMove(nowMs: Long): Result {
        val echo = nets.size >= 2
        val screen = screenHeight()
        val parts = nets.entries.joinToString(" ") { "${short(it.key)}=${it.value}" }.ifEmpty { "-" }

        // This burst's own biggest mover, before any carry is added.
        val raw = nets.entries.filter { it.value != 0 }.maxByOrNull { abs(it.value) }

        // Proving the page by repetition: the same view moving the same
        // distance twice in a row is a pager, whatever the screen height says.
        val prev = lastCandidate
        if (pageHeight == null && raw != null && prev != null && prev.first == raw.key &&
            sameSize(prev.second, raw.value) && abs(raw.value) >= screen * MIN_PAIR_OF_SCREEN
        ) {
            pageHeight = abs(raw.value)
            pagerClasses += raw.key
            val flips = (if (raw.value > 0) 1 else 0) + (if (prev.second > 0) 1 else 0)
            clearCarry()
            lastCandidate = null
            return Result(flips, echo, "page $parts echo=${yn(echo)} size=$pageHeight → +$flips (size learned from a repeat)")
        }

        // Judge the burst as it is, and again with any half-finished swipe
        // carried in; the carry is only used when it turns a miss into a page,
        // so a stale one can never cost a real swipe its count.
        val plain = pickBest(nets, echo, screen)
        var carryUsed = 0
        var chosen = plain
        val carried = if (carry != 0 && nowMs - carryAtMs <= CARRY_MS) carryClass else null
        if (carried != null) {
            val withCarry = LinkedHashMap(nets).apply { this[carried] = (this[carried] ?: 0) + carry }
            val joined = pickBest(withCarry, echo, screen)
            val plainFlips = plain?.third?.flips ?: 0
            // Better if it turns a miss into a page. Before the page size is
            // known, also on a tie: the second half of a split first swipe can
            // look like a (too small) page on its own, and would teach it.
            val better = joined != null && (
                joined.third.flips > plainFlips ||
                    (pageHeight == null && joined.third.flips > 0 && joined.third.flips == plainFlips)
                )
            if (better) {
                chosen = joined
                carryUsed = carry
            }
        }
        clearCarry()
        if (chosen == null) return Result(0, echo, "page $parts echo=${yn(echo)} → 0 (no movement)")
        val (cls, net, verdict) = chosen
        val flips = verdict.flips

        verdict.learnedPageHeight?.let { pageHeight = it }
        if (flips > 0) pagerClasses += cls

        val atBoundary = isBoundary(net)
        // Remember this burst's own move (not the carry) as a possible repeat.
        lastCandidate = when {
            flips > 0 || pageHeight != null -> null
            raw != null && abs(raw.value) >= screen * MIN_PAIR_OF_SCREEN -> raw.key to raw.value
            else -> lastCandidate
        }

        // Stopped part-way through a page on the Shorts screen: hold on to it,
        // the rest of the swipe is probably coming. A small move inside a
        // Short (a panel sliding up 288px) is not part of a swipe.
        val partMin = (pageHeight ?: screen).times(CARRY_MIN_OF_PAGE)
        if (flips == 0 && !atBoundary && echo && abs(net) >= partMin) {
            carry = net
            carryClass = cls
            carryAtMs = nowMs
        }

        val trace = buildString {
            append("page ").append(parts)
            if (carryUsed != 0) append(" carry=").append(carryUsed)
            append(" echo=").append(yn(echo))
            append(" size=").append(pageHeight ?: "?")
            append(" → ").append(if (flips > 0) "+$flips" else "0")
            append(" (").append(if (flips > 0) "page" else "no page")
            if (carry != 0) append(", holding ").append(carry)
            append(')')
        }
        return Result(flips, echo, trace)
    }

    private fun pickBest(nets: Map<String, Int>, echo: Boolean, screen: Int): Triple<String, Int, PageFlip.Verdict>? {
        var best: Triple<String, Int, PageFlip.Verdict>? = null
        for ((cls, net) in nets) {
            if (net == 0) continue
            val verdict = PageFlip.judge(net, echo, pageHeight, screen)
            val current = best
            val better = current == null ||
                verdict.flips > current.third.flips ||
                (verdict.flips == current.third.flips && prefer(cls, current.first, net, current.second))
            if (better) best = Triple(cls, net, verdict)
        }
        return best
    }

    private fun clearCarry() {
        carry = 0
        carryClass = null
    }

    /** Whole pages away, back or forward, or back where it started. */
    private fun isBoundary(net: Int): Boolean {
        if (abs(net) < HOLD_MIN_DELTA) return true
        val page = pageHeight ?: return abs(net) >= screenHeight() * PAGE_OF_SCREEN_MIN
        val pages = Math.round(abs(net).toFloat() / page)
        return pages >= 1 && abs(abs(net) - pages * page) <= maxOf(24, (pages * page * 0.03f).toInt())
    }

    private fun prefer(cls: String, other: String, net: Int, otherNet: Int): Boolean {
        val mine = preferredClassHints.any { cls.lowercase().contains(it) }
        val theirs = preferredClassHints.any { other.lowercase().contains(it) }
        if (mine != theirs) return mine
        return abs(net) > abs(otherNet)
    }

    private fun sameSize(a: Int, b: Int): Boolean {
        val x = abs(a)
        val y = abs(b)
        return abs(x - y) <= maxOf(8, (maxOf(x, y) * 0.01f).toInt())
    }

    private fun short(cls: String) = cls.substringAfterLast('.')
    private fun yn(b: Boolean) = if (b) "y" else "n"

    companion object {
        /** A move this big (px) is a swipe, not a ticker; it may hold a burst open. */
        const val HOLD_MIN_DELTA = 40

        /** No burst lasts longer than this, however busy the screen is. */
        const val MAX_BURST_MS = 3_000L

        /** How long a half-finished swipe waits for the rest of it. */
        const val CARRY_MS = 4_000L

        /**
         * A repeat must be at least this share of the screen to prove a page.
         * A Short fills the screen bar the status bar and YouTube's tabs; at a
         * quarter, two equal scrolls of a long video's page (710px) passed
         * as Shorts and put the pill over a normal video.
         */
        const val MIN_PAIR_OF_SCREEN = 0.7f

        /** A held-over part of a swipe must be at least this share of a page. */
        const val CARRY_MIN_OF_PAGE = 0.25f

        /** Before the page is known, a move this big is treated as a whole page. */
        const val PAGE_OF_SCREEN_MIN = PageFlip.MIN_PAGE_OF_SCREEN

        /** Nobody swipes back further than this to rewatch; a bound, not a rule. */
        const val MAX_BEHIND = 50
    }
}
