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

    fun send(signal: ScrollSignal) = apply { events += detector.onSignal(signal) }
    fun tick(atMs: Long) = apply { events += detector.onTick(atMs) }
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
        assertTrue(h.detector.onTick(500_000).isEmpty())
    }
}
