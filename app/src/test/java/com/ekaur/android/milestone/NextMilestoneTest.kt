package com.ekaur.android.milestone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextMilestoneTest {

    private val rules = listOf(
        Milestone("reels_100", Trigger.CountReached(100), "k"),
        Milestone("night", Trigger.ClockBetween(hour = 1), "k"),
        Milestone("reels_25", Trigger.CountReached(25), "k"),
        Milestone("reels_50", Trigger.CountReached(50), "k"),
        Milestone("session", Trigger.SessionMinutes(30), "k"),
    )

    @Test
    fun `only count rungs make the ladder, ascending`() {
        assertEquals(listOf(25, 50, 100), NextMilestone.ladder(rules))
    }

    @Test
    fun `zero points at the first rung`() {
        val roast = NextMilestone.forCount(0, rules)
        assertEquals(0, roast.previous)
        assertEquals(25, roast.next)
        assertEquals(25, roast.toGo)
        assertEquals(0f, roast.fraction, 0.0001f)
    }

    @Test
    fun `mid-way between rungs`() {
        val roast = NextMilestone.forCount(75, rules)
        assertEquals(50, roast.previous)
        assertEquals(100, roast.next)
        assertEquals(25, roast.toGo)
        assertEquals(0.5f, roast.fraction, 0.0001f)
    }

    @Test
    fun `sitting exactly on a rung aims at the next one`() {
        val roast = NextMilestone.forCount(50, rules)
        assertEquals(50, roast.previous)
        assertEquals(100, roast.next)
    }

    @Test
    fun `past the top rung there is no next`() {
        val roast = NextMilestone.forCount(140, rules)
        assertNull(roast.next)
        assertEquals(1f, roast.fraction, 0.0001f)
        assertEquals(0, roast.toGo)
    }

    @Test
    fun `negative counts are treated as zero`() {
        assertEquals(25, NextMilestone.forCount(-5, rules).next)
    }

    @Test
    fun `the real ladder is non-empty and starts small`() {
        val rungs = NextMilestone.ladder()
        assertTrue(rungs.isNotEmpty())
        assertEquals(25, rungs.first())
    }
}
