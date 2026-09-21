package com.ekaur.android.ui

import com.ekaur.android.data.remote.LeaderboardRow
import com.ekaur.android.ui.friends.DemoLeaderboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLeaderboardTest {

    @Test
    fun `a day has a full set of seed users with sane counts`() {
        val rows = DemoLeaderboard.rowsFor("2026-09-21")
        assertEquals(10, rows.size)
        assertTrue(rows.all { it.reelCount in 1..2000 })
        assertTrue(rows.all { it.username.isNotBlank() })
    }

    @Test
    fun `the same day is stable`() {
        assertEquals(
            DemoLeaderboard.rowsFor("2026-09-21").map { it.reelCount },
            DemoLeaderboard.rowsFor("2026-09-21").map { it.reelCount },
        )
    }

    @Test
    fun `different days give different numbers`() {
        val a = DemoLeaderboard.rowsFor("2026-09-21").map { it.reelCount }
        val b = DemoLeaderboard.rowsFor("2026-09-22").map { it.reelCount }
        assertTrue("the board must move day to day", a != b)
    }

    @Test
    fun `blending keeps a real person and ranks by reels`() {
        val me = LeaderboardRow("me-id", "hakkan", 40, 0, null)
        val blended = DemoLeaderboard.blend(listOf(me), "2026-09-21")

        // My real row survives, and the whole list is sorted high-to-low.
        assertTrue(blended.any { it.username == "hakkan" && it.userId == "me-id" })
        assertEquals(blended.sortedByDescending { it.reelCount }, blended)
    }

    @Test
    fun `a real username shadows the seed of the same name`() {
        val realRana = LeaderboardRow("real-rana", "rana", 999, 0, null)
        val blended = DemoLeaderboard.blend(listOf(realRana), "2026-09-21")

        // Only one "rana", and it is the real one.
        val ranas = blended.filter { it.username.equals("rana", ignoreCase = true) }
        assertEquals(1, ranas.size)
        assertEquals("real-rana", ranas.first().userId)
    }
}
