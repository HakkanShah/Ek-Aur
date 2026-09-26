package com.ekaur.android.sync

import com.ekaur.android.detect.TrackedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePlanTest {

    private fun day(date: String, total: Int, reels: Int = 0, shorts: Int = 0, activeMs: Long = 0) =
        ServerDay(date, total, reels, shorts, activeMs)

    @Test
    fun `a wiped phone takes every server day`() {
        val server = listOf(day("2026-09-26", 51), day("2026-09-25", 900))
        assertEquals(server, RestorePlan.daysToWrite(server, emptyMap()))
    }

    @Test
    fun `nothing the phone counted is ever lowered`() {
        val server = listOf(day("2026-09-26", 51), day("2026-09-25", 900))
        val local = mapOf("2026-09-26" to 80, "2026-09-25" to 900)
        // Today the phone has more (not uploaded yet); yesterday is equal.
        assertTrue(RestorePlan.daysToWrite(server, local).isEmpty())
    }

    @Test
    fun `a day the server has more of is taken from the server`() {
        val server = listOf(day("2026-09-26", 300))
        assertEquals(server, RestorePlan.daysToWrite(server, mapOf("2026-09-26" to 50)))
    }

    @Test
    fun `empty server days are skipped`() {
        assertTrue(RestorePlan.daysToWrite(listOf(day("2026-09-20", 0)), emptyMap()).isEmpty())
    }

    @Test
    fun `reels and shorts go back to their own apps, marked as already synced`() {
        val rows = RestorePlan.rowsFor(day("2026-09-26", 51, reels = 20, shorts = 31, activeMs = 600_000), 0, nowMs = 99)
        assertEquals(2, rows.size)
        val ig = rows.first { it.packageName == TrackedApp.Instagram.packageName }
        val yt = rows.first { it.packageName == TrackedApp.YouTube.packageName }
        assertEquals(20, ig.reelCount)
        assertEquals(31, yt.reelCount)
        assertEquals(600_000L, ig.activeMs + yt.activeMs)
        assertTrue(rows.none { it.dirty })
        assertTrue(rows.all { it.lastSyncedAtMs == 99L })
    }

    @Test
    fun `a day from before the split counts as reels`() {
        val rows = RestorePlan.rowsFor(day("2026-09-20", 700), 0, nowMs = 1)
        assertEquals(1, rows.size)
        assertEquals(TrackedApp.Instagram.packageName, rows.single().packageName)
        assertEquals(700, rows.single().reelCount)
    }

    @Test
    fun `time watched keeps the larger of phone and server`() {
        val rows = RestorePlan.rowsFor(day("2026-09-26", 10, activeMs = 1_000), localActiveMs = 5_000, nowMs = 1)
        assertEquals(5_000L, rows.sumOf { it.activeMs })
    }
}
