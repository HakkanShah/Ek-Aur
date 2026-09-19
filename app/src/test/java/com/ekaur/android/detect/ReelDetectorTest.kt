package com.ekaur.android.detect

import com.ekaur.android.detect.ScrollSignal.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IG = "com.instagram.android"
private const val OTHER = "com.whatsapp"

/** Instagram's Reels player coming to the foreground. */
private fun enterReels(t: Long) = ScrollSignal(
    packageName = IG,
    kind = Kind.WindowStateChanged,
    timestampMs = t,
    className = "com.instagram.clips.viewer.ClipsViewerFragment",
    viewId = "com.instagram.android:id/clips_viewer_root",
)

/** The main feed -- a tracked app, but nothing that should ever count. */
private fun enterFeed(t: Long) = ScrollSignal(
    packageName = IG,
    kind = Kind.WindowStateChanged,
    timestampMs = t,
    className = "com.instagram.mainfeed.MainFeedFragment",
    viewId = "com.instagram.android:id/main_feed_recycler",
)

/** A scroll that reports the pager position -- the accurate path. */
private fun scrollTo(t: Long, index: Int) = ScrollSignal(
    packageName = IG,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    viewId = "com.instagram.android:id/clips_viewer_view_pager",
    itemIndex = index,
)

/** A scroll with no position reported -- the settle-window fallback path. */
private fun scrollBy(t: Long, dy: Int, pkg: String = IG) = ScrollSignal(
    packageName = pkg,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    viewId = "com.instagram.android:id/clips_viewer_view_pager",
    scrollDeltaY = dy,
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

    // --- accurate, index-based path ---------------------------------------

    @Test
    fun `counts one reel per pager advance`() {
        val h = Harness().send(enterReels(0))
        // First scroll only establishes the baseline position.
        h.send(scrollTo(100, 0))
        h.send(scrollTo(1_000, 1))
        h.send(scrollTo(2_000, 2))
        h.send(scrollTo(3_000, 3))

        assertEquals(3, h.reelCount())
    }

    @Test
    fun `scrolling back up does not count`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 5))
        h.send(scrollTo(1_000, 6))   // forward: counts
        h.send(scrollTo(2_000, 5))   // back up: does not
        h.send(scrollTo(3_000, 4))   // still going back: does not

        assertEquals(1, h.reelCount())
    }

    @Test
    fun `a jump in position is capped rather than inventing counts`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 0))
        h.send(scrollTo(1_000, 40))  // nonsense delta from a noisy event

        assertTrue("should be capped, was ${h.reelCount()}", h.reelCount() <= 3)
    }

    // --- settle-window fallback -------------------------------------------

    @Test
    fun `one swipe firing many scroll events counts once`() {
        val h = Harness().send(enterReels(0))
        // A single swipe: a burst of events over ~150ms.
        listOf(100L, 130L, 160L, 190L, 220L, 250L).forEach { h.send(scrollBy(it, 60)) }
        h.tick(700)   // quiet long enough to settle

        assertEquals(1, h.reelCount())
    }

    @Test
    fun `separate swipes each count once`() {
        val h = Harness().send(enterReels(0))

        listOf(100L, 140L, 180L).forEach { h.send(scrollBy(it, 70)) }
        h.tick(600)
        listOf(1_000L, 1_040L, 1_080L).forEach { h.send(scrollBy(it, 70)) }
        h.tick(1_500)

        assertEquals(2, h.reelCount())
    }

    @Test
    fun `a nudge too small to be a swipe does not count`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollBy(100, 5))     // below minNetScroll
        h.tick(700)

        assertEquals(0, h.reelCount())
    }

    @Test
    fun `dragging back up does not count on the fallback path`() {
        val h = Harness().send(enterReels(0))
        listOf(100L, 140L, 180L).forEach { h.send(scrollBy(it, -80)) }
        h.tick(700)

        assertEquals(0, h.reelCount())
    }

    // --- state discrimination ---------------------------------------------

    @Test
    fun `scrolling the main feed never counts`() {
        val h = Harness().send(enterFeed(0))
        listOf(100L, 200L, 300L, 400L).forEach {
            h.send(
                ScrollSignal(
                    packageName = IG,
                    kind = Kind.ViewScrolled,
                    timestampMs = it,
                    viewId = "com.instagram.android:id/main_feed_recycler",
                    scrollDeltaY = 200,
                )
            )
        }
        h.tick(1_000)

        assertEquals(0, h.reelCount())
        assertEquals(DetectionState.InApp, h.detector.state)
    }

    @Test
    fun `leaving reels for DMs stops counting`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 0))
        h.send(scrollTo(500, 1))          // counts

        h.send(
            ScrollSignal(
                packageName = IG,
                kind = Kind.WindowStateChanged,
                timestampMs = 1_000,
                className = "com.instagram.direct.DirectThreadFragment",
                viewId = "com.instagram.android:id/direct_thread_recycler",
            )
        )
        // Scrolling a DM thread reports the thread's own view, not the player's.
        h.send(
            ScrollSignal(
                packageName = IG,
                kind = Kind.ViewScrolled,
                timestampMs = 1_200,
                viewId = "com.instagram.android:id/direct_thread_recycler",
                scrollDeltaY = 300,
            )
        )
        h.tick(2_000)

        assertEquals(1, h.reelCount())
        assertEquals(DetectionState.InApp, h.detector.state)
    }

    @Test
    fun `a player scroll re-enters reels even if the window event was missed`() {
        // Accessibility window events do get dropped. A scroll that is
        // unmistakably the player should be enough to resume counting on its
        // own, rather than going silent until the next window change.
        val h = Harness().send(enterFeed(0))
        h.send(scrollTo(1_000, 4))
        h.send(scrollTo(2_000, 5))

        assertEquals(DetectionState.InReels, h.detector.state)
        assertEquals(1, h.reelCount())
    }

    @Test
    fun `an untracked app is ignored entirely`() {
        val h = Harness()
        h.send(
            ScrollSignal(
                packageName = OTHER,
                kind = Kind.WindowStateChanged,
                timestampMs = 0,
                className = "com.whatsapp.Conversation",
            )
        )
        h.send(scrollBy(100, 500, pkg = OTHER))
        h.tick(1_000)

        assertEquals(0, h.reelCount())
        assertEquals(DetectionState.Idle, h.detector.state)
    }

    @Test
    fun `a content change elsewhere does not knock us out of reels`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 0))
        // Some unrelated subtree updates; this must not end Reels.
        h.send(
            ScrollSignal(
                packageName = IG,
                kind = Kind.WindowContentChanged,
                timestampMs = 500,
                className = "android.widget.FrameLayout",
            )
        )
        h.send(scrollTo(1_000, 1))

        assertEquals(1, h.reelCount())
        assertEquals(DetectionState.InReels, h.detector.state)
    }

    // --- backgrounding ----------------------------------------------------

    @Test
    fun `backgrounding mid-swipe flushes exactly once`() {
        val h = Harness().send(enterReels(0))
        listOf(100L, 140L, 180L).forEach { h.send(scrollBy(it, 80)) }

        // User leaves before the settle window elapses.
        h.send(
            ScrollSignal(
                packageName = OTHER,
                kind = Kind.WindowStateChanged,
                timestampMs = 250,
                className = "com.whatsapp.Conversation",
            )
        )
        h.tick(2_000)

        assertEquals(1, h.reelCount())
    }

    // --- sessions ---------------------------------------------------------

    @Test
    fun `a session opens on entering reels and closes when it goes cold`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 0))
        h.send(scrollTo(1_000, 1))
        h.send(scrollTo(2_000, 2))

        assertEquals(1, h.events.filterIsInstance<DetectionEvent.SessionStarted>().size)

        // Past the session gap with no activity.
        h.tick(2_000 + 120_000)

        val ended = h.events.filterIsInstance<DetectionEvent.SessionEnded>()
        assertEquals(1, ended.size)
        assertEquals(2, ended.single().reelCount)
    }

    @Test
    fun `a brief detour out of reels keeps one session`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 0))
        h.send(scrollTo(500, 1))

        h.send(enterFeed(1_000))          // stepped out
        h.send(enterReels(3_000))         // and straight back
        h.send(scrollTo(3_100, 0))
        h.send(scrollTo(3_500, 1))

        assertEquals(
            "detour should not start a second session",
            1,
            h.events.filterIsInstance<DetectionEvent.SessionStarted>().size,
        )
        assertEquals(2, h.reelCount())
    }

    @Test
    fun `reset clears everything`() {
        val h = Harness().send(enterReels(0))
        h.send(scrollTo(100, 0))
        h.send(scrollTo(500, 1))

        h.detector.reset()

        assertEquals(DetectionState.Idle, h.detector.state)
        assertTrue(h.detector.onTick(500_000).isEmpty())
    }
}
