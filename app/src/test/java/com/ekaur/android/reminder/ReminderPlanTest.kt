package com.ekaur.android.reminder

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlanTest {

    private val on = ReminderSettings(enabled = true, at = 100, snooze = 20)
    private val today = "2026-09-26"

    @Test
    fun `fires on the reel that reaches the number`() {
        val day = ReminderPlan.dayFor(on, null, today)
        assertFalse(ReminderPlan.shouldRemind(on, day, count = 99, before = 98))
        assertTrue(ReminderPlan.shouldRemind(on, day, count = 100, before = 99))
    }

    @Test
    fun `off means never`() {
        val off = on.copy(enabled = false)
        assertFalse(ReminderPlan.shouldRemind(off, ReminderPlan.dayFor(off, null, today), 100, 99))
    }

    @Test
    fun `a count that did not move never fires, so a restart mid-day is quiet`() {
        val day = ReminderPlan.dayFor(on, null, today)
        assertFalse(ReminderPlan.shouldRemind(on, day, count = 150, before = 150))
    }

    @Test
    fun `showing it already schedules the next one, N more away`() {
        val shown = ReminderPlan.onShown(ReminderPlan.dayFor(on, null, today), count = 100, settings = on)
        assertEquals(120, shown.nextAt)
        assertFalse(ReminderPlan.shouldRemind(on, shown, count = 101, before = 100))
        assertTrue(ReminderPlan.shouldRemind(on, shown, count = 120, before = 119))
    }

    @Test
    fun `not today stays quiet until tomorrow, then starts again`() {
        val quiet = ReminderPlan.onNotToday(ReminderPlan.dayFor(on, null, today))
        assertNull(quiet.nextAt)
        assertFalse(ReminderPlan.shouldRemind(on, quiet, count = 500, before = 499))

        val tomorrow = ReminderPlan.dayFor(on, quiet, "2026-09-27")
        assertEquals(100, tomorrow.nextAt)
    }

    @Test
    fun `a number already passed pops up on the next reel`() {
        val day = ReminderPlan.onSettingsChanged(on, today)
        assertTrue(ReminderPlan.shouldRemind(on, day, count = 151, before = 150))
        assertEquals(ReminderPlan.Status.OnNextReel(100), ReminderPlan.status(on, day, 150))
    }

    @Test
    fun `status reads the day plainly`() {
        val fresh = ReminderPlan.dayFor(on, null, today)
        assertEquals(ReminderPlan.Status.Waiting(100, 23, snoozed = false), ReminderPlan.status(on, fresh, 77))
        val snoozed = ReminderPlan.onShown(fresh, 100, on)
        assertEquals(ReminderPlan.Status.Waiting(120, 15, snoozed = true), ReminderPlan.status(on, snoozed, 105))
        assertEquals(ReminderPlan.Status.DoneForToday, ReminderPlan.status(on, ReminderPlan.onNotToday(fresh), 105))
        assertEquals(ReminderPlan.Status.Off, ReminderPlan.status(on.copy(enabled = false), fresh, 105))
    }

    @Test
    fun `picked numbers are kept sensible`() {
        assertEquals(ReminderSettings.MIN_AT, ReminderPlan.clampAt(0))
        assertEquals(ReminderSettings.MAX_AT, ReminderPlan.clampAt(1_000_000))
    }

    // --- the watcher, against a live count ------------------------------

    private class FakeStore(override var reminderSettings: ReminderSettings) : ReminderStore {
        override var reminderDay: ReminderDay? = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the watcher fires once at the number, then again N later, never from stored counts`() = runTest {
        val counts = MutableStateFlow(95) // today's stored total when the service starts
        val store = FakeStore(on.copy(at = 90))
        val fired = mutableListOf<Int>()
        val watcher = ReminderWatcher(
            scope = backgroundScope,
            counts = counts,
            store = store,
            today = { today },
            onRemind = { fired += it },
        )
        // Nothing yet: 95 is past 90, but no reel has been seen.
        kotlinx.coroutines.yield()
        counts.value = 96
        kotlinx.coroutines.yield()
        assertEquals(emptyList<Int>(), fired)

        watcher.onReelCounted()
        counts.value = 97
        kotlinx.coroutines.yield()
        assertEquals(listOf(97), fired)          // past 90: on this reel

        for (n in 98..116) { counts.value = n; kotlinx.coroutines.yield() }
        assertEquals(listOf(97), fired)          // quiet for 20
        counts.value = 117
        kotlinx.coroutines.yield()
        assertEquals(listOf(97, 117), fired)     // and again
    }
}
