package com.ekaur.android.detect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ways a phone other than the realme could see the Shorts screen and
 * still count nothing -- which is what the server showed for a friend on
 * build 52 -- each replayed through the full detector.
 */
class PageTrackerTest {

    private val yt = "com.google.android.youtube"
    private val rv = "android.support.v7.widget.RecyclerView"
    private val group = "android.view.ViewGroup"
    private val frame = "android.widget.FrameLayout"

    private var t = 1_000_000L
    private val detector = ReelDetector()
    private val counted = mutableListOf<DetectionEvent.ReelScrolled>()

    private fun scroll(cls: String, dy: Int, at: Long = t) {
        counted += detector.onSignal(
            ScrollSignal(yt, ScrollSignal.Kind.ViewScrolled, at, className = cls, scrollDeltaY = dy),
        ).events.filterIsInstance<DetectionEvent.ReelScrolled>()
    }

    /** One swipe as the realme reports it: the list, plus two containers echoing it. */
    private fun swipe(vararg parts: Int, pager: String = rv, echo: Boolean = true) {
        parts.forEachIndexed { i, dy ->
            scroll(pager, dy)
            if (echo) {
                // The containers shift and come back: they move, but net ~0.
                val wobble = if (i % 2 == 0) 7 else -7
                scroll(group, wobble, t + 1)
                scroll(frame, wobble, t + 1)
            }
            t += 110
        }
    }

    private fun idle(ms: Long) {
        val end = t + ms
        while (t < end) {
            t += 250
            counted += detector.onTick(t).events.filterIsInstance<DetectionEvent.ReelScrolled>()
        }
    }

    @Test
    fun `plain swipes count one each`() {
        repeat(5) { swipe(1275, 820, 5); idle(1_000) }
        assertEquals(5, counted.size)
    }

    @Test
    fun `a swipe held half way and then finished still counts`() {
        swipe(1275, 820, 5); idle(1_000) // learns the page
        swipe(500, 300); idle(700)        // held: 800 so far, settles
        swipe(900, 395, 5); idle(1_000)   // finished: 1300 more
        assertEquals(2, counted.size)
    }

    @Test
    fun `the very first swipe arriving in two pieces teaches the right page`() {
        swipe(500, 300); idle(700)        // 800
        swipe(900, 395, 5); idle(1_000)   // + 1300 = 2100
        swipe(1275, 820, 5); idle(1_000)  // a normal 2100 must still count
        assertEquals(2, counted.size)
    }

    @Test
    fun `a ticker scrolling every 200ms never blocks a swipe`() {
        val ticker = "com.example.Marquee"
        swipe(1275, 820, 5)
        repeat(20) { scroll(ticker, 6); t += 200; counted += detector.onTick(t).events.filterIsInstance<DetectionEvent.ReelScrolled>() }
        swipe(1275, 820, 5)
        repeat(20) { scroll(ticker, 6); t += 200; counted += detector.onTick(t).events.filterIsInstance<DetectionEvent.ReelScrolled>() }
        idle(1_000)
        assertEquals(2, counted.size)
        assertEquals(DetectionState.InReels, detector.state)
    }

    @Test
    fun `a pager reporting some other class still counts`() {
        // The list moves as a ViewGroup; the RecyclerView only wobbles.
        repeat(3) {
            scroll(group, 1275); scroll(rv, 30, t + 1); t += 110
            scroll(group, 820); scroll(rv, -30, t + 1); t += 110
            scroll(group, 5); t += 110
            idle(1_000)
        }
        assertEquals(3, counted.size)
    }

    @Test
    fun `a page smaller than half the screen is learned from a repeat`() {
        // 900px pages on a 2400px screen, and no echo at all.
        swipe(600, 300, echo = false); idle(1_000)
        swipe(600, 300, echo = false); idle(1_000)
        swipe(600, 300, echo = false); idle(1_000)
        assertEquals(3, counted.size)
    }

    @Test
    fun `feed flings of any length never count before a page is known`() {
        swipe(900, 600, 250, echo = false); idle(1_000)   // 1750
        swipe(1500, 900, 200, echo = false); idle(1_000)  // 2600
        assertEquals(0, counted.size)
    }

    @Test
    fun `swiping back and a drag that snaps back never count`() {
        swipe(1275, 820, 5); idle(1_000)
        swipe(-1508, -592); idle(1_000)
        swipe(44, -39, -5); idle(1_000)
        swipe(288); idle(1_500); swipe(-288); idle(1_500)
        swipe(1549, 551); idle(1_000)   // forward onto the Short just left: a rewatch
        swipe(1275, 820, 5); idle(1_000) // a new one
        assertEquals(2, counted.size)
    }

    @Test
    fun `watching one long Short keeps the player on screen`() {
        swipe(1275, 820, 5)
        idle(180_000) // a three-minute Short, no touches
        assertEquals(DetectionState.InReels, detector.state)
    }

    @Test
    fun `a list scrolling non stop is still judged, and counts nothing`() {
        swipe(1275, 820, 5); idle(1_000)
        repeat(60) { scroll(rv, 35); t += 100 } // an auto-scrolling list, 6s
        idle(1_000)
        assertEquals(1, counted.size)
    }

    @Test
    fun `each judged burst is traced`() {
        val lines = mutableListOf<String>()
        val traced = ReelDetector(trace = { _, line -> lines += line })
        traced.onSignal(ScrollSignal(yt, ScrollSignal.Kind.ViewScrolled, 1_000, className = rv, scrollDeltaY = 2100))
        traced.onSignal(ScrollSignal(yt, ScrollSignal.Kind.ViewScrolled, 1_001, className = group, scrollDeltaY = 0))
        traced.onSignal(ScrollSignal(yt, ScrollSignal.Kind.ViewScrolled, 1_001, className = frame, scrollDeltaY = 40))
        traced.onTick(2_000)
        assertEquals(1, lines.size)
        assert(lines.single().contains("+1")) { lines.single() }
    }

    @Test
    fun `swiping back to rewatch a Short and forward again counts nothing extra`() {
        repeat(3) { swipe(1275, 820, 5); idle(1_000) }   // three new: 3
        swipe(-1508, -592); idle(1_000)                    // back one
        swipe(-1400, -700); idle(1_000)                    // back two
        swipe(1275, 820, 5); idle(1_000)                   // rewatch
        swipe(776, 1138, 186); idle(1_000)                 // rewatch
        swipe(1275, 820, 5); idle(1_000)                   // new: 4

        assertEquals(4, counted.size)
    }

    @Test
    fun `an up-scroll outside Shorts never blocks later Shorts from counting`() {
        swipe(1275, 820, 5); idle(1_000)                   // learns the page: 1
        // A lone list scrolled up exactly a page, with no Shorts echo.
        swipe(-2100, echo = false); idle(1_000)
        swipe(1275, 820, 5); idle(1_000)                   // still new: 2

        assertEquals(2, counted.size)
    }
}
