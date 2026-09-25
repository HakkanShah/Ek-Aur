package com.ekaur.android.detect

import com.ekaur.android.detect.ScrollSignal.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val YT = "com.google.android.youtube"
private const val INSTA = "com.instagram.android"

/**
 * YouTube Shorts as the service sees it on a phone: no view ids (the service
 * cannot read the screen), a class name, positions, and scroll deltas.
 */
private fun shortsScroll(t: Long, index: Int, dy: Int = 1800, pkg: String = YT) = ScrollSignal(
    packageName = pkg,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.viewpager2.widget.ViewPager2",
    scrollDeltaY = dy,
    fromIndex = index,
    toIndex = index,
)

/** A paged RecyclerView mid-swipe: two videos partly on screen. */
private fun midSwipe(t: Long, from: Int, dy: Int = 600) = ScrollSignal(
    packageName = YT,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.recyclerview.widget.RecyclerView",
    scrollDeltaY = dy,
    fromIndex = from,
    toIndex = from + 1,
)

/** The same paged RecyclerView once the swipe settles on one video. */
private fun settled(t: Long, index: Int) = ScrollSignal(
    packageName = YT,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.recyclerview.widget.RecyclerView",
    scrollDeltaY = 0,
    fromIndex = index,
    toIndex = index,
)

/** The YouTube home feed: several cards visible at once. */
private fun homeFeed(t: Long, from: Int, to: Int) = ScrollSignal(
    packageName = YT,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.recyclerview.widget.RecyclerView",
    scrollDeltaY = 900,
    fromIndex = from,
    toIndex = to,
)

/** A one-page-at-a-time pager swiped sideways: a channel's tab strip. */
private fun sidewaysPager(t: Long, index: Int, pkg: String = YT) = ScrollSignal(
    packageName = pkg,
    kind = Kind.ViewScrolled,
    timestampMs = t,
    className = "androidx.viewpager.widget.ViewPager",
    scrollDeltaX = 1080,
    scrollDeltaY = 0,
    fromIndex = index,
    toIndex = index,
)

private fun window(t: Long, pkg: String) = ScrollSignal(
    packageName = pkg,
    kind = Kind.WindowStateChanged,
    timestampMs = t,
    className = "x.Main",
)

private class ShortsHarness(rulesFor: (String) -> AppRules? = DetectorRules::forPackage) {
    val detector = ReelDetector(rulesFor)
    val events = mutableListOf<DetectionEvent>()
    fun send(signal: ScrollSignal) = apply { events += detector.onSignal(signal).events }
    fun reels() = events.filterIsInstance<DetectionEvent.ReelScrolled>()
}

class ShortsDetectionTest {

    @Test
    fun `youtube is a tracked app`() {
        assertEquals(YT, DetectorRules.forPackage(YT)?.packageName)
        assertEquals(TrackedApp.YouTube, TrackedApp.forPackage(YT))
        assertEquals(TrackedApp.Instagram, TrackedApp.forPackage(INSTA))
    }

    @Test
    fun `fifteen shorts swiped count fifteen`() {
        val h = ShortsHarness()
        h.send(window(0, YT))
        for (i in 0..15) h.send(shortsScroll(1_000L + i * 3_000L, i))
        // Position 0 is the baseline the player reports on opening.
        assertEquals(15, h.reels().size)
        assertTrue(h.reels().all { it.packageName == YT })
        assertEquals(DetectionState.InReels, h.detector.state)
    }

    @Test
    fun `a paged list counts once per settled swipe, not per mid-swipe event`() {
        val h = ShortsHarness()
        h.send(settled(0, 0))
        var t = 1_000L
        for (i in 0 until 10) {
            h.send(midSwipe(t, i)); t += 80
            h.send(midSwipe(t, i)); t += 80
            h.send(settled(t, i + 1)); t += 3_000
        }
        assertEquals(10, h.reels().size)
    }

    @Test
    fun `the youtube home feed never counts and never enters the player`() {
        val h = ShortsHarness()
        h.send(window(0, YT))
        for (i in 0 until 30) h.send(homeFeed(1_000L + i * 400L, i, i + 2))
        assertEquals(0, h.reels().size)
        assertEquals(DetectionState.InApp, h.detector.state)
    }

    @Test
    fun `a sideways one-page pager never counts, in either app`() {
        for (pkg in listOf(YT, INSTA)) {
            val h = ShortsHarness()
            for (i in 0 until 5) h.send(sidewaysPager(1_000L + i * 500L, i, pkg))
            assertEquals(pkg, 0, h.reels().size)
        }
    }

    @Test
    fun `a diagonal scroll with vertical movement still counts`() {
        val h = ShortsHarness()
        h.send(shortsScroll(0, 0))
        h.send(shortsScroll(1_000, 1).copy(scrollDeltaX = 40))
        assertEquals(1, h.reels().size)
    }

    @Test
    fun `without deltas (android 8) the shape rule still counts shorts`() {
        val h = ShortsHarness()
        h.send(shortsScroll(0, 0, dy = 0))
        h.send(shortsScroll(1_000, 1, dy = 0))
        h.send(shortsScroll(2_000, 2, dy = 0))
        assertEquals(2, h.reels().size)
    }

    @Test
    fun `scrolling back up in shorts does not count`() {
        val h = ShortsHarness()
        h.send(shortsScroll(0, 3))
        h.send(shortsScroll(1_000, 4))        // counts
        h.send(shortsScroll(2_000, 3, dy = -1800))
        h.send(shortsScroll(3_000, 2, dy = -1800))
        assertEquals(1, h.reels().size)
    }

    @Test
    fun `reopening shorts at position zero is a fresh start, not a lost count`() {
        val h = ShortsHarness()
        h.send(shortsScroll(0, 0))
        for (i in 1..6) h.send(shortsScroll(i * 1_000L, i))       // 6
        h.send(homeFeed(8_000, 0, 3))                             // back on home
        h.send(shortsScroll(12_000, 0))                           // new shorts feed
        for (i in 1..4) h.send(shortsScroll(12_000L + i * 1_000L, i))  // 4 more
        assertEquals(10, h.reels().size)
    }

    @Test
    fun `hopping between instagram and youtube keeps both counts exact and labelled`() {
        val h = ShortsHarness()
        // Instagram Reels: 0..3 -> 3
        h.send(window(0, INSTA))
        for (i in 0..3) h.send(shortsScroll(1_000L + i * 1_000L, i, pkg = INSTA))
        // YouTube Shorts: 0..5 -> 5
        h.send(window(10_000, YT))
        for (i in 0..5) h.send(shortsScroll(11_000L + i * 1_000L, i))
        // Back to Instagram, a fresh player at 10..12 -> 2
        h.send(window(20_000, INSTA))
        for (i in 10..12) h.send(shortsScroll(21_000L + (i - 10) * 1_000L, i, pkg = INSTA))

        val reels = h.reels()
        assertEquals(10, reels.size)
        assertEquals(5, reels.count { it.packageName == INSTA })
        assertEquals(5, reels.count { it.packageName == YT })
    }

    @Test
    fun `switching apps never carries a position across and invents a jump`() {
        val h = ShortsHarness()
        for (i in 0..2) h.send(shortsScroll(i * 1_000L, i, pkg = INSTA))       // 2
        // YouTube's player happens to sit at a higher index than Instagram's.
        h.send(window(5_000, YT))
        h.send(shortsScroll(6_000, 40))                                          // baseline only
        assertEquals(2, h.reels().size)
    }

    @Test
    fun `sessions end when the user hops apps, one per app`() {
        val h = ShortsHarness()
        for (i in 0..2) h.send(shortsScroll(i * 1_000L, i, pkg = INSTA))
        h.send(window(5_000, YT))
        for (i in 0..2) h.send(shortsScroll(6_000L + i * 1_000L, i))
        val ended = h.events.filterIsInstance<DetectionEvent.SessionEnded>()
        assertEquals(1, ended.size)
        assertEquals(INSTA, ended.single().packageName)
        assertEquals(2, ended.single().reelCount)
    }

    @Test
    fun `with shorts counting switched off youtube is ignored entirely`() {
        val onlyInstagram: (String) -> AppRules? = { pkg ->
            DetectorRules.forPackage(pkg)?.takeIf { it.packageName == INSTA }
        }
        val h = ShortsHarness(onlyInstagram)
        for (i in 0..8) h.send(shortsScroll(i * 1_000L, i))
        assertEquals(0, h.reels().size)
        assertEquals(DetectionState.Idle, h.detector.state)
        // Instagram still counts.
        for (i in 0..3) h.send(shortsScroll(20_000L + i * 1_000L, i, pkg = INSTA))
        assertEquals(3, h.reels().size)
    }

    @Test
    fun `a notification from youtube during reels does not end the instagram session`() {
        // Only tracked apps send events, but a stray YouTube window event (a
        // picture-in-picture video, say) while Reels is open is a genuine app
        // change by the rules; the next Instagram scroll re-baselines without
        // a false count.
        val h = ShortsHarness()
        for (i in 0..3) h.send(shortsScroll(i * 1_000L, i, pkg = INSTA))     // 3
        h.send(window(4_500, YT))
        h.send(shortsScroll(5_000, 4, pkg = INSTA))                           // baseline again
        h.send(shortsScroll(6_000, 5, pkg = INSTA))                           // 1
        assertEquals(4, h.reels().size)
    }
}
