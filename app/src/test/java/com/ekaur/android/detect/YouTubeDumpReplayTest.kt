package com.ekaur.android.detect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replays a real event dump from a phone (realme RMX3151, Android 13) through
 * the detector: YouTube's home screen, Shorts swiped by hand, a detour to
 * Instagram Reels, then more Shorts.
 *
 * YouTube's Shorts pager reports no positions, which is why build 51 counted
 * none of these. Counted by distance, every forward flip in the dump counts:
 * 11 of them. The three swipes back, the drag that snapped back and the
 * +288/-288 moves inside a Short do not.
 */
class YouTubeDumpReplayTest {

    private val youtube = "com.google.android.youtube"

    @Test
    fun `every new Short in the real dump counts once, and rewatches don't`() {
        val detector = ReelDetector()
        val events = replay(detector, load())
        val shorts = events.filterIsInstance<DetectionEvent.ReelScrolled>().filter { it.packageName == youtube }
        // 11 forward swipes, three of them straight after a swipe back to the
        // previous Short: those land on a Short already counted.
        assertEquals(8, shorts.size)
    }

    @Test
    fun `the counts land on the forward swipes to new Shorts`() {
        val detector = ReelDetector()
        val times = replay(detector, load())
            .filterIsInstance<DetectionEvent.ReelScrolled>()
            .filter { it.packageName == youtube }
            .map { it.timestampMs }
        // Each forward swipe's last real movement of the list, read off the dump.
        assertEquals(
            // Missing from the 11 forward swipes: 104495, 414776 and 439374,
            // each the swipe back onto a Short just rewatched.
            listOf(100343L, 404141L, 405101L, 406709L, 416518L, 429326L, 432847L, 455637L),
            times,
        )
    }

    @Test
    fun `the player is recognised, so the pill shows over Shorts`() {
        val detector = ReelDetector()
        val signals = load()
        val firstSwipeEnd = signals.indexOfFirst { it.timestampMs == 100506L }
        replay(detector, signals.take(firstSwipeEnd + 1))
        detector.onTick(100506L + 400)
        assertEquals(DetectionState.InReels, detector.state)
    }

    // --- replay ------------------------------------------------------------

    private fun replay(detector: ReelDetector, signals: List<ScrollSignal>): List<DetectionEvent> {
        val out = mutableListOf<DetectionEvent>()
        var last = 0L
        for (signal in signals) {
            // The service ticks every 250ms; do the same between events.
            var t = last + 250
            while (last > 0 && t < signal.timestampMs) {
                out += detector.onTick(t).events
                t += 250
            }
            out += detector.onSignal(signal).events
            last = signal.timestampMs
        }
        out += detector.onTick(last + 5_000).events
        return out
    }

    private fun load(): List<ScrollSignal> {
        val text = javaClass.classLoader!!.getResource("dumps/youtube_shorts_realme_rmx3151.txt")!!.readText()
        val header = Regex("""^(\d+)\s+(\w+)\s+state=""")
        val signals = mutableListOf<ScrollSignal>()
        var current: MutableMap<String, String>? = null
        fun flush() {
            val c = current ?: return
            val kind = when (c["type"]) {
                "ViewScrolled" -> ScrollSignal.Kind.ViewScrolled
                "WindowStateChanged" -> ScrollSignal.Kind.WindowStateChanged
                else -> return
            }
            signals += ScrollSignal(
                packageName = c.getValue("pkg"),
                kind = kind,
                timestampMs = c.getValue("t").toLong(),
                className = c["class"],
                contentDescription = c["desc"],
                scrollDeltaY = if (kind == ScrollSignal.Kind.ViewScrolled) c["scrollDeltaY"]?.toInt() ?: 0 else 0,
                fromIndex = c["fromIndex"]?.toInt() ?: ScrollSignal.NO_INDEX,
                toIndex = c["toIndex"]?.toInt() ?: ScrollSignal.NO_INDEX,
            )
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            val h = header.find(line)
            if (h != null) {
                flush()
                current = mutableMapOf("t" to h.groupValues[1], "type" to h.groupValues[2])
                continue
            }
            val c = current ?: continue
            when {
                line.startsWith("pkg=") -> c["pkg"] = line.removePrefix("pkg=")
                line.startsWith("class=") -> c["class"] = line.removePrefix("class=")
                line.startsWith("desc=") -> c["desc"] = line.removePrefix("desc=")
                line.startsWith("scrollDeltaY=") || line.startsWith("fromIndex=") ->
                    line.split(' ').forEach { part ->
                        val (k, v) = part.split('=', limit = 2)
                        c[k] = v
                    }
            }
        }
        flush()
        return signals
    }
}
