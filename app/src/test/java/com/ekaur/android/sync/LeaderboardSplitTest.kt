package com.ekaur.android.sync

import com.ekaur.android.data.remote.LeaderboardSplit
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardSplitTest {

    @Test
    fun `an older app's row with no split reads as all reels`() {
        assertEquals(LeaderboardSplit(reels = 91, shorts = 0), LeaderboardSplit.of(91, 0, 0))
    }

    @Test
    fun `a consistent split is taken as is`() {
        assertEquals(LeaderboardSplit(120, 45), LeaderboardSplit.of(165, 120, 45))
    }

    @Test
    fun `shorts only`() {
        assertEquals(LeaderboardSplit(0, 30), LeaderboardSplit.of(30, 0, 30))
    }

    @Test
    fun `a split that does not add up never exceeds the total`() {
        assertEquals(LeaderboardSplit(60, 40), LeaderboardSplit.of(100, 10, 40))
        assertEquals(LeaderboardSplit(0, 100), LeaderboardSplit.of(100, 0, 500))
    }

    @Test
    fun `a zero day is zero everywhere`() {
        assertEquals(LeaderboardSplit(0, 0), LeaderboardSplit.of(0, 0, 0))
    }
}
