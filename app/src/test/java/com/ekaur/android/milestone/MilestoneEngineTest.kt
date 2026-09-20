package com.ekaur.android.milestone

import com.ekaur.android.copy.SarcasmCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOON = 12 * 60

/** Progress at midday, so no clock milestone interferes unless asked for. */
private fun at(reels: Int, sessionMinutes: Int = 0, minuteOfDay: Int = NOON) =
    Progress(reels = reels, sessionMinutes = sessionMinutes, minuteOfDay = minuteOfDay)

class MilestoneEngineTest {

    private val engine = MilestoneEngine()

    @Test
    fun `a count milestone fires when it is reached`() {
        val fired = engine.evaluate(at(50), at(49), emptySet())

        assertEquals("reels_50", fired?.announce?.id)
    }

    @Test
    fun `a threshold stepped over is not missed`() {
        // Two reels can land in one update, so the count never has to be exact.
        val fired = engine.evaluate(at(101), at(98), emptySet())

        assertEquals("reels_100", fired?.announce?.id)
    }

    @Test
    fun `a milestone already used today stays quiet`() {
        val fired = engine.evaluate(at(100), at(99), setOf("reels_50", "reels_100"))

        assertNull(fired)
    }

    @Test
    fun `a high count with an empty log does not work through every threshold`() {
        // What a device looks like the first day this ships, or after its data
        // is cleared: 300 reels already counted today and nothing recorded as
        // fired. Announcing 50, then 100, then 200 over three reels would be
        // nonsense, so the passed ones are retired without being shown.
        val outcome = engine.evaluate(at(300), at(299), emptySet())

        assertEquals("reels_200", outcome?.announce?.id)
        assertEquals(
            "smaller thresholds should be retired, not queued up",
            setOf("reels_50", "reels_100", "reels_200"),
            outcome?.spent?.toSet(),
        )
    }

    @Test
    fun `nothing fires when the count has not moved`() {
        // Idle ticks must never produce a line, or the small-hours milestones
        // would fire while the phone sits on a table.
        assertNull(engine.evaluate(at(100, minuteOfDay = 3 * 60), at(100, minuteOfDay = 3 * 60), emptySet()))
        assertNull(engine.evaluate(at(99), at(100), emptySet()))
    }

    @Test
    fun `a sitting crossing half an hour fires`() {
        val fired = engine.evaluate(
            now = at(40, sessionMinutes = 30),
            before = at(39, sessionMinutes = 29),
            firedToday = emptySet(),
        )

        assertEquals("session_30", fired?.announce?.id)
    }

    @Test
    fun `a new sitting does not refire a duration passed in the last one`() {
        // The session clock resets to zero, so nothing is satisfied again.
        val fired = engine.evaluate(
            now = at(40, sessionMinutes = 1),
            before = at(39, sessionMinutes = 0),
            firedToday = emptySet(),
        )

        assertNull(fired)
    }

    @Test
    fun `the small hours fire on the first reel after the time`() {
        val before3am = engine.evaluate(
            now = at(40, minuteOfDay = 2 * 60 + 59),
            before = at(39, minuteOfDay = 2 * 60 + 59),
            firedToday = setOf("night_1am"),
        )
        val after3am = engine.evaluate(
            now = at(40, minuteOfDay = 3 * 60),
            before = at(39, minuteOfDay = 3 * 60),
            firedToday = setOf("night_1am"),
        )

        assertNull("3am fired before 3am", before3am)
        assertEquals("night_3am", after3am?.announce?.id)
    }

    @Test
    fun `late evening is not the small hours`() {
        // "Past 1am" without an end to the window is also true at 11pm, which
        // is neither late nor funny.
        val fired = engine.evaluate(
            now = at(40, minuteOfDay = 23 * 60),
            before = at(39, minuteOfDay = 23 * 60),
            firedToday = emptySet(),
        )

        assertNull(fired)
    }

    @Test
    fun `the small hours end in the morning`() {
        val fired = engine.evaluate(
            now = at(40, minuteOfDay = 5 * 60),
            before = at(39, minuteOfDay = 5 * 60),
            firedToday = emptySet(),
        )

        assertNull(fired)
    }

    @Test
    fun `only one line arrives at a time, and the rest wait their turn`() {
        // A century reached at 3am during a two-hour sitting: three milestones
        // become eligible on one reel. Firing them together would stack three
        // lines on a pill that shows one.
        val now = at(100, sessionMinutes = 120, minuteOfDay = 3 * 60)
        val before = at(99, sessionMinutes = 119, minuteOfDay = 3 * 60)

        val first = engine.evaluate(now, before, emptySet())
        assertEquals("the most notable should lead", "session_120", first?.announce?.id)

        // The shorter sittings are retired with it -- a two-hour sitting has
        // nothing left to say about half an hour.
        val fired = first!!.spent.toMutableSet()
        assertTrue("session_30 should be retired", "session_30" in fired)
        assertTrue("session_60 should be retired", "session_60" in fired)

        // The ones that still mean something arrive on the following reels,
        // one at a time, rather than being lost to the tie-break.
        val next = at(101, sessionMinutes = 121, minuteOfDay = 3 * 60)
        val second = engine.evaluate(next, now, fired)
        assertEquals("night_3am", second?.announce?.id)
        fired += second!!.spent

        val third = engine.evaluate(
            now = at(102, sessionMinutes = 122, minuteOfDay = 3 * 60),
            before = next,
            firedToday = fired,
        )
        assertEquals("reels_100", third?.announce?.id)
    }

    @Test
    fun `every rule has copy of its own`() {
        // A rule whose key has no lines silently falls back to the recurring
        // copy, so the milestone fires and says something generic instead.
        // Cheaper to catch here than to notice at 3am on a device.
        val missing = MilestoneRules.DEFAULT
            .map { it.copyKey }
            .filterNot { it in SarcasmCatalogue.copyKeys }

        assertEquals("keys with no lines written", emptyList<String>(), missing)
    }

    @Test
    fun `the small hours windows do not overlap`() {
        // Two windows covering the same minute would let both fire on
        // consecutive reels, and one of the two lines would be telling the
        // wrong time.
        val nights = MilestoneRules.DEFAULT
            .map { it.trigger }
            .filterIsInstance<Trigger.ClockBetween>()

        for (minute in 0 until 24 * 60) {
            val covering = nights.count {
                minute >= it.hour * 60 + it.minute && minute < it.untilHour * 60
            }
            if (covering > 1) throw AssertionError("minute $minute is in $covering windows")
        }
    }

    @Test
    fun `ids are unique, so one cannot mask another forever`() {
        val ids = MilestoneRules.DEFAULT.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }
}
