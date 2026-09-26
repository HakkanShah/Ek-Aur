package com.ekaur.android.detect

import com.ekaur.android.detect.ScrollSignal.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IG = "com.instagram.android"
private const val OTHER = "com.whatsapp"

private const val PLAYER_VIEW = "com.instagram.android:id/clips_viewer_view_pager"
private const val FEED_VIEW = "com.instagram.android:id/feed_recycler_view"

/**
 * A scroll in the Reels player: one full-screen item, so both positions agree.
 * Shape taken from a real device dump.
 */
private fun playerScroll(t: Long, index: Int, view: String = PLAYER_VIEW) = ScrollSignal(
    packageName = IG,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.viewpager.widget.ViewPager",
    viewId = view,
    fromIndex = index,
    toIndex = index,
)

/** A scroll in the main feed: several items visible, so the positions differ. */
private fun feedScroll(t: Long, from: Int, to: Int, desc: String? = null) = ScrollSignal(
    packageName = IG,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.recyclerview.widget.RecyclerView",
    viewId = FEED_VIEW,
    contentDescription = desc,
    fromIndex = from,
    toIndex = to,
)

/** A scroll reporting no positions at all -- exercises the fallback path. */
private fun blindScroll(
    t: Long,
    dy: Int,
    pkg: String = IG,
    view: String? = PLAYER_VIEW,
) = ScrollSignal(
    packageName = pkg,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.viewpager.widget.ViewPager",
    viewId = view,
    scrollDeltaY = dy,
)

private fun contentEvent(t: Long, desc: String) = ScrollSignal(
    packageName = IG,
    kind = Kind.WindowContentChanged,
    timestampMs = t,
    className = "android.view.ViewGroup",
    contentDescription = desc,
)

private fun foreground(t: Long, pkg: String) = ScrollSignal(
    packageName = pkg,
    kind = Kind.WindowStateChanged,
    timestampMs = t,
    className = if (pkg == IG) "com.instagram.mainactivity.InstagramMainActivity" else "x.Main",
)

private fun List<DetectionEvent>.reels() = filterIsInstance<DetectionEvent.ReelScrolled>()

private class Harness {
    val detector = ReelDetector()
    val events = mutableListOf<DetectionEvent>()

    fun send(signal: ScrollSignal) = apply { events += detector.onSignal(signal).events }
    fun tick(atMs: Long) = apply { events += detector.onTick(atMs).events }
    fun reelCount() = events.reels().size
}

class ReelDetectorTest {

    // --- the real dump ----------------------------------------------------

    @Test
    fun `replays the recorded device sequence and counts three advances`() {
        // Verbatim from a device dump: positions 11,11,12,12,12,13,13,14,14.
        // Three genuine advances. The first version of the detector scored 1
        // here because string matching kept flipping it out of the player and
        // resetting the baseline.
        val h = Harness()
        listOf(
            774912L to 11, 775231L to 11,
            776124L to 12, 776308L to 12, 776334L to 12,
            777759L to 13, 777924L to 13,
            778746L to 14, 779671L to 14,
        ).forEach { (t, i) -> h.send(playerScroll(t, i)) }

        assertEquals(3, h.reelCount())
    }

    @Test
    fun `feed scroll shapes from the dump never count`() {
        // Also verbatim: the feed reports a span of visible items, never one.
        val h = Harness()
        listOf(
            25 to 27, 25 to 28, 26 to 28, 27 to 30, 30 to 33,
            36 to 43, 43 to 47, 47 to 53,
        ).forEachIndexed { i, (from, to) -> h.send(feedScroll(1_000L * i, from, to)) }
        h.tick(20_000)

        assertEquals(0, h.reelCount())
        assertEquals(DetectionState.InApp, h.detector.state)
    }

    // --- the bugs the dump exposed ---------------------------------------

    @Test
    fun `a comment description on the reels screen does not stop counting`() {
        // The Reels comment button reports "Comment number is35. View comments",
        // which the old rules read as not-the-player and bailed out on.
        val h = Harness()
        h.send(playerScroll(0, 5))
        h.send(contentEvent(100, "Comment number is35. View comments"))
        h.send(playerScroll(500, 6))
        h.send(contentEvent(600, "Comment number is67. View comments"))
        h.send(playerScroll(1_000, 7))

        assertEquals(2, h.reelCount())
        assertEquals(DetectionState.InReels, h.detector.state)
    }

    @Test
    fun `the feed's Reels tab description does not start counting`() {
        // The main feed contains a tab described literally as "Reels", which the
        // old rules treated as proof the player was open.
        val h = Harness()
        h.send(contentEvent(0, "Reels"))
        h.send(feedScroll(100, 10, 13, desc = "Reels"))
        h.send(feedScroll(500, 13, 16, desc = "Reels"))
        h.tick(2_000)

        assertEquals(0, h.reelCount())
    }

    @Test
    fun `feed scrolling between reels does not reset the player baseline`() {
        // Positions are tracked per view, so a detour through the feed cannot
        // make the next reel look like a fresh first sighting.
        val h = Harness()
        h.send(playerScroll(0, 20))        // baseline
        h.send(playerScroll(500, 21))      // counts
        h.send(feedScroll(1_000, 40, 45))  // detour
        h.send(feedScroll(1_200, 45, 50))
        h.send(playerScroll(2_000, 22))    // must still count
        h.send(playerScroll(2_500, 23))

        assertEquals(3, h.reelCount())
    }

    @Test
    fun `window events alone never enter the player`() {
        // Instagram is a single-Activity app: window events only ever report
        // InstagramMainActivity, so they can never mean "Reels opened".
        val h = Harness()
        h.send(foreground(0, IG))
        h.send(foreground(1_000, IG))
        h.send(contentEvent(1_500, "clips_viewer_root"))

        assertEquals(DetectionState.InApp, h.detector.state)
        assertEquals(0, h.reelCount())
    }

    @Test
    fun `instagram's tab strip is never counted as reels`() {
        // swipeable_tab_view_pager shows one tab at a time, so it reports the
        // same shape as the Reels player. Swiping between Home, Search, Reels
        // and Profile would otherwise count as four reels.
        val h = Harness()
        (0..4).forEach { i ->
            h.send(
                playerScroll(1_000L * i, i, view = "com.instagram.android:id/swipeable_tab_view_pager")
            )
        }
        h.tick(10_000)

        assertEquals(0, h.reelCount())
    }

    @Test
    fun `tab swiping between reels sessions does not count`() {
        val h = Harness()
        h.send(playerScroll(0, 4))
        h.send(playerScroll(500, 5))       // a real reel
        h.send(playerScroll(1_000, 0, view = "com.instagram.android:id/swipeable_tab_view_pager"))
        h.send(playerScroll(1_500, 1, view = "com.instagram.android:id/swipeable_tab_view_pager"))
        h.send(playerScroll(2_000, 6))     // back in reels, still counts

        assertEquals(2, h.reelCount())
    }

    @Test
    fun `a background list scroll during reels does not drop out of the player`() {
        // Measured on device: Instagram scrolls android:id/list while Reels is
        // open, about 100ms after a player scroll. Treating that as leaving made
        // the state flip constantly, which made the floating counter blink.
        val h = Harness()
        h.send(playerScroll(0, 10))
        h.send(feedScroll(98, 0, 3))          // the interloper
        h.send(playerScroll(900, 11))
        h.send(feedScroll(998, 0, 3))
        h.send(playerScroll(1_800, 12))

        assertEquals(
            "player must stay on screen through interleaved list scrolls",
            DetectionState.InReels,
            h.detector.state,
        )
        assertEquals(2, h.reelCount())
    }

    @Test
    fun `a list scroll well after the player went quiet does leave`() {
        val h = Harness()
        h.send(playerScroll(0, 10))
        h.send(playerScroll(500, 11))
        // Device data showed a real exit left a multi-second gap.
        h.send(feedScroll(4_200, 0, 3))

        assertEquals(DetectionState.InApp, h.detector.state)
    }

    @Test
    fun `leaving reels for DMs stops counting`() {
        val h = Harness()
        h.send(playerScroll(0, 0))
        h.send(playerScroll(500, 1))          // counts

        // A DM thread scrolls its own view, and getting there takes a moment.
        h.send(
            ScrollSignal(
                packageName = IG,
                kind = Kind.ViewScrolled,
                timestampMs = 5_000,
                className = "androidx.recyclerview.widget.RecyclerView",
                viewId = "com.instagram.android:id/direct_thread_recycler",
                scrollDeltaY = 300,
                fromIndex = 2,
                toIndex = 6,
            )
        )
        h.tick(6_000)

        assertEquals(1, h.reelCount())
        assertEquals(DetectionState.InApp, h.detector.state)
    }

    @Test
    fun `sitting on one reel does not immediately leave the player`() {
        // Watching a single reel through produces no scrolls at all; that must
        // not be mistaken for closing Reels.
        val h = Harness()
        h.send(playerScroll(0, 3))
        h.tick(8_000)

        assertEquals(DetectionState.InReels, h.detector.state)
    }

    @Test
    fun `watching a long reel through does not close the player`() {
        // Reels routinely run 15-60s with no scroll at all. The old 12s idle
        // timeout fired while the user was simply watching, and took the
        // floating counter down with it.
        val h = Harness()
        h.send(playerScroll(0, 7))

        listOf(5_000L, 10_000L, 20_000L, 30_000L, 40_000L).forEach { h.tick(it) }

        assertEquals(DetectionState.InReels, h.detector.state)
    }

    @Test
    fun `believing the user left costs the next advance its count`() {
        // Documents why the service checks the real foreground before trusting a
        // window event from another package: a notification that was mistaken
        // for an app switch landed here, and silently ate a reel.
        val withoutInterruption = Harness()
        withoutInterruption.send(playerScroll(0, 10))
        withoutInterruption.send(playerScroll(500, 11))
        withoutInterruption.send(playerScroll(1_000, 12))

        val interrupted = Harness()
        interrupted.send(playerScroll(0, 10))
        interrupted.send(playerScroll(500, 11))
        interrupted.send(foreground(700, OTHER))   // as if the user really left
        interrupted.send(playerScroll(1_000, 12))

        assertEquals(2, withoutInterruption.reelCount())
        assertEquals(
            "a believed app switch resets the baseline, so the next reel is free",
            1,
            interrupted.reelCount(),
        )
    }

    @Test
    fun `leaving reels with no other scrolling eventually closes the player`() {
        val h = Harness()
        h.send(playerScroll(0, 3))
        h.tick(60_000)

        assertEquals(DetectionState.InApp, h.detector.state)
    }

    // --- counting behaviour ----------------------------------------------

    @Test
    fun `fifteen swipes count fourteen, losing only the baseline`() {
        // A swipe produces a scroll event after the fact, so 15 swipes arrive as
        // 15 events. The first establishes the starting position, leaving 14
        // observable advances. This is the number to expect on device.
        val h = Harness()
        repeat(15) { i -> h.send(playerScroll(1_000L * i, 20 + i)) }

        assertEquals(14, h.reelCount())
    }

    @Test
    fun `repeated events at the same position count once`() {
        val h = Harness()
        h.send(playerScroll(0, 3))
        repeat(6) { i -> h.send(playerScroll(100L * i + 100, 4)) }

        assertEquals(1, h.reelCount())
    }

    @Test
    fun `scrolling back up does not count`() {
        val h = Harness()
        h.send(playerScroll(0, 5))
        h.send(playerScroll(500, 6))    // counts
        h.send(playerScroll(1_000, 5))  // back up
        h.send(playerScroll(1_500, 4))

        assertEquals(1, h.reelCount())
    }

    @Test
    fun `going back to rewatch and forward again does not count twice`() {
        val h = Harness()
        h.send(playerScroll(0, 10))
        h.send(playerScroll(500, 11))    // new: counts
        h.send(playerScroll(1_000, 12))  // new: counts
        h.send(playerScroll(1_500, 11))  // back to rewatch
        h.send(playerScroll(2_000, 10))  // further back
        h.send(playerScroll(2_500, 11))  // seen already
        h.send(playerScroll(3_000, 12))  // seen already
        h.send(playerScroll(3_500, 13))  // new again: counts

        assertEquals(3, h.reelCount())
    }

    @Test
    fun `a fresh reels list after a big jump back counts from the start`() {
        // Reels reopened from the tab bar: the pager starts again near 0.
        val h = Harness()
        h.send(playerScroll(0, 20))
        h.send(playerScroll(500, 21))   // counts
        h.send(playerScroll(1_000, 0))  // new list, not 21 swipes back
        h.send(playerScroll(1_500, 1))  // counts
        h.send(playerScroll(2_000, 2))  // counts

        assertEquals(3, h.reelCount())
    }

    @Test
    fun `the detector rests only when nothing is waiting on time`() {
        val d = ReelDetector()
        assertTrue(d.isResting)
        d.onSignal(playerScroll(0, 5))
        assertTrue("in the player it needs the fast tick", !d.isResting)
        d.onTick(10 * 60_000L)  // the player idles out and the sitting goes cold
        assertTrue(d.isResting)
    }

    @Test
    fun `a wild jump in position is capped`() {
        val h = Harness()
        h.send(playerScroll(0, 0))
        h.send(playerScroll(1_000, 40))

        assertTrue("expected a cap, got ${h.reelCount()}", h.reelCount() <= 3)
    }

    // --- fallback path ----------------------------------------------------

    @Test
    fun `a swipe reporting no positions counts once via the settle window`() {
        val h = Harness()
        h.send(playerScroll(0, 1))  // establishes that the player is on screen
        listOf(100L, 130L, 160L, 190L).forEach { h.send(blindScroll(it, 60)) }
        h.tick(700)

        assertEquals(1, h.reelCount())
    }

    @Test
    fun `the fallback never runs outside the player`() {
        // A positionless scroll from some unrelated view, while the feed is what
        // was last seen. Nothing here says "player", so nothing may count.
        val h = Harness()
        h.send(feedScroll(0, 10, 14))
        listOf(100L, 130L, 160L).forEach {
            h.send(blindScroll(it, 200, view = "com.instagram.android:id/some_other_view"))
        }
        h.tick(1_000)

        assertEquals(0, h.reelCount())
        assertEquals(DetectionState.InApp, h.detector.state)
    }

    @Test
    fun `a positionless scroll from the player's own view still counts`() {
        // The id alone is proof enough of which surface produced it, even when
        // the event carries no position.
        val h = Harness()
        listOf(100L, 130L, 160L).forEach { h.send(blindScroll(it, 80)) }
        h.tick(700)

        assertEquals(1, h.reelCount())
    }

    @Test
    fun `backgrounding mid-swipe flushes exactly once`() {
        val h = Harness()
        h.send(playerScroll(0, 1))
        listOf(100L, 140L, 180L).forEach { h.send(blindScroll(it, 80)) }
        h.send(foreground(250, OTHER))
        h.tick(2_000)

        assertEquals(1, h.reelCount())
    }

    // --- lifecycle --------------------------------------------------------

    @Test
    fun `an untracked app is ignored entirely`() {
        val h = Harness()
        h.send(foreground(0, OTHER))
        h.send(blindScroll(100, 500, pkg = OTHER))
        h.tick(1_000)

        assertEquals(0, h.reelCount())
        assertEquals(DetectionState.Idle, h.detector.state)
    }

    @Test
    fun `a session opens on the first reel and closes when it goes cold`() {
        val h = Harness()
        h.send(playerScroll(0, 0))
        h.send(playerScroll(1_000, 1))
        h.send(playerScroll(2_000, 2))

        assertEquals(1, h.events.filterIsInstance<DetectionEvent.SessionStarted>().size)

        h.tick(2_000 + 120_000)

        val ended = h.events.filterIsInstance<DetectionEvent.SessionEnded>()
        assertEquals(1, ended.size)
        assertEquals(2, ended.single().reelCount)
    }

    @Test
    fun `a feed detour keeps one session`() {
        val h = Harness()
        h.send(playerScroll(0, 0))
        h.send(playerScroll(500, 1))
        h.send(feedScroll(1_000, 20, 24))
        h.send(playerScroll(3_000, 2))

        assertEquals(1, h.events.filterIsInstance<DetectionEvent.SessionStarted>().size)
        assertEquals(2, h.reelCount())
    }

    @Test
    fun `a cold session clears stale positions`() {
        val h = Harness()
        h.send(playerScroll(0, 50))
        h.send(playerScroll(500, 51))
        h.tick(500 + 120_000)   // session goes cold

        // Coming back at an unrelated position must not register as a huge jump.
        h.send(playerScroll(200_000, 4))
        h.send(playerScroll(201_000, 5))

        assertEquals(2, h.reelCount())
    }

    @Test
    fun `reset clears everything`() {
        val h = Harness()
        h.send(playerScroll(0, 0))
        h.send(playerScroll(500, 1))

        h.detector.reset()

        assertEquals(DetectionState.Idle, h.detector.state)
        assertTrue(h.detector.onTick(500_000).events.isEmpty())
    }

    // --- concurrency ------------------------------------------------------

    @Test
    fun `signals and ticks from different threads agree on the count`() {
        // The service feeds signals from the accessibility callback on the main
        // thread while a coroutine ticks on a background dispatcher. Both reach
        // the same burst accumulator, so unguarded they can each see an open
        // burst and each emit for it -- or trample the position map, which on
        // device lost 19 of 38 advances in one contiguous run.
        //
        // The burst path is used here because it is the one both entry points
        // write to, which makes the race reachable without relying on luck.
        val detector = ReelDetector()
        val swipes = 4_000
        val clock = java.util.concurrent.atomic.AtomicLong(1_000_000L)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val fromTicks = java.util.concurrent.atomic.AtomicInteger(0)
        val tickerFailed = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)

        val ticker = Thread {
            try {
                while (!stop.get()) {
                    fromTicks.addAndGet(
                        detector.onTick(clock.get())
                            .events
                            .count { it is DetectionEvent.ReelScrolled }
                    )
                }
            } catch (t: Throwable) {
                tickerFailed.set(t)
            }
        }.apply { start() }

        var fromSignals = 0
        try {
            repeat(swipes) {
                // One swipe: a few positionless scrolls, then time enough for
                // the burst to settle while the ticker is hammering away.
                repeat(3) {
                    fromSignals += detector.onSignal(blindScroll(clock.addAndGet(20), 90))
                        .events
                        .count { e -> e is DetectionEvent.ReelScrolled }
                }
                clock.addAndGet(400)
                Thread.yield()
            }
            // Let any final burst settle.
            clock.addAndGet(2_000)
            Thread.sleep(50)
        } finally {
            stop.set(true)
            ticker.join(10_000)
        }

        assertEquals("ticker threw: ${tickerFailed.get()}", null, tickerFailed.get())
        // Exactly one count per swipe, wherever it happened to be emitted.
        assertEquals(swipes, fromSignals + fromTicks.get())
    }

    @Test
    fun `replays the full third dump and loses nothing`() {
        // Positions 29 through 67 as recorded on device: 38 advances after the
        // baseline. The racing build counted 19.
        val h = Harness()
        var t = 1_000L
        for (index in 29..67) {
            h.send(playerScroll(t, index))
            t += 400
            h.send(playerScroll(t, index))   // the duplicate each swipe emits
            t += 600
        }

        assertEquals(38, h.reelCount())
    }
}
