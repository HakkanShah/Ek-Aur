package com.ekaur.android.sync

import com.ekaur.android.data.local.DailyCountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IG = "com.instagram.android"

private fun row(date: String, reels: Int, activeMs: Long = 0, dirty: Boolean = true) =
    DailyCountEntity(
        date = date,
        packageName = IG,
        reelCount = reels,
        activeMs = activeMs,
        dirty = dirty,
    )

class SyncPlanTest {

    @Test
    fun `only the date, the count and the time are ever uploaded`() {
        // The shape of DayUpload is the privacy promise. If a raw event could be
        // attached to an upload, it would have to appear here first.
        val upload = SyncPlan.toUpload(listOf(row("2026-09-20", 91, 600_000))).single()

        assertEquals("2026-09-20", upload.date)
        assertEquals(91, upload.reelCount)
        assertEquals(600_000L, upload.activeMs)
    }

    @Test
    fun `days go up oldest first`() {
        // A partial success then leaves the recent days dirty, and those are the
        // ones a friend is actually looking at.
        val plan = SyncPlan.toUpload(
            listOf(row("2026-09-20", 3), row("2026-09-18", 1), row("2026-09-19", 2)),
        )

        assertEquals(listOf("2026-09-18", "2026-09-19", "2026-09-20"), plan.map { it.date })
    }

    @Test
    fun `a long time offline still sends a bounded batch`() {
        val many = (1..200).map { row("2026-01-%02d".format((it % 28) + 1), it) }

        assertTrue(SyncPlan.toUpload(many).size <= SyncPlan.MAX_BATCH)
    }

    @Test
    fun `a row that did not change is marked clean`() {
        val sent = SyncPlan.toUpload(listOf(row("2026-09-20", 91, 600_000)))
        val current = listOf(row("2026-09-20", 91, 600_000))

        assertEquals(1, SyncPlan.syncedRows(sent, current).size)
    }

    @Test
    fun `a row that grew mid-upload stays dirty`() {
        // The whole reason syncedRows exists. Counting does not stop while the
        // request is in flight, and clearing on "the request succeeded" alone
        // would silently drop whatever was scrolled during it.
        val sent = SyncPlan.toUpload(listOf(row("2026-09-20", 91)))
        val current = listOf(row("2026-09-20", 94))

        assertTrue(SyncPlan.syncedRows(sent, current).isEmpty())
    }

    @Test
    fun `time spent changing also keeps a row dirty`() {
        val sent = SyncPlan.toUpload(listOf(row("2026-09-20", 91, 600_000)))
        val current = listOf(row("2026-09-20", 91, 900_000))

        assertTrue(SyncPlan.syncedRows(sent, current).isEmpty())
    }

    @Test
    fun `a day that was never sent is never marked clean`() {
        val sent = SyncPlan.toUpload(listOf(row("2026-09-20", 91)))
        val current = listOf(row("2026-09-20", 91), row("2026-09-21", 4))

        assertEquals(listOf("2026-09-20"), SyncPlan.syncedRows(sent, current).map { it.date })
    }

    @Test
    fun `nothing dirty means nothing to send`() {
        assertTrue(SyncPlan.toUpload(emptyList()).isEmpty())
    }

    @Test
    fun `a token is refreshed before it expires, not after a rejection`() {
        val now = 1_000_000L
        val skew = TokenState.SKEW_MS

        assertFalse(TokenState.needsRefresh("t", now + skew + 1, now))
        assertTrue("should refresh inside the skew window", TokenState.needsRefresh("t", now + skew - 1, now))
        assertTrue(TokenState.needsRefresh("t", now, now))
    }

    @Test
    fun `anything missing counts as expired`() {
        val now = 1_000_000L

        assertTrue(TokenState.needsRefresh(null, now + 999_999, now))
        assertTrue(TokenState.needsRefresh("", now + 999_999, now))
        assertTrue(TokenState.needsRefresh("t", null, now))
    }

    @Test
    fun `an expiry is computed once from a lifetime`() {
        assertEquals(1_000_000L + 3_600_000L, TokenState.expiryFrom(1_000_000L, 3600))
    }
}

private const val YT = "com.google.android.youtube"

private fun ytRow(date: String, shorts: Int, activeMs: Long = 0, dirty: Boolean = true) =
    DailyCountEntity(
        date = date,
        packageName = YT,
        reelCount = shorts,
        activeMs = activeMs,
        dirty = dirty,
    )

class SyncPlanTwoAppsTest {

    @Test
    fun `reels and shorts on one day go up as one summed row with the split`() {
        val up = SyncPlan.toUpload(
            listOf(row("2026-09-25", 120, 600_000), ytRow("2026-09-25", 45, 300_000)),
        ).single()

        assertEquals(165, up.reelCount)
        assertEquals(900_000L, up.activeMs)
        assertEquals(120, up.reelsCount)
        assertEquals(45, up.shortsCount)
    }

    @Test
    fun `a day with only shorts reports zero reels`() {
        val up = SyncPlan.toUpload(listOf(ytRow("2026-09-25", 30))).single()
        assertEquals(30, up.reelCount)
        assertEquals(0, up.reelsCount)
        assertEquals(30, up.shortsCount)
    }

    @Test
    fun `the batch limit counts days, not rows`() {
        val rows = (1..70).flatMap { d ->
            val date = "2026-%02d-%02d".format((d / 28) + 1, (d % 28) + 1)
            listOf(row(date, d), ytRow(date, d))
        }
        val plan = SyncPlan.toUpload(rows)
        assertEquals(SyncPlan.MAX_BATCH, plan.size)
        assertEquals(plan.map { it.date }.distinct().size, plan.size)
    }

    @Test
    fun `dates to send are distinct and oldest first`() {
        val dirty = listOf(row("2026-09-25", 1), ytRow("2026-09-25", 2), row("2026-09-24", 3))
        assertEquals(listOf("2026-09-24", "2026-09-25"), SyncPlan.datesToSend(dirty))
    }

    @Test
    fun `an unchanged day settles both apps' rows`() {
        val rows = listOf(row("2026-09-25", 120), ytRow("2026-09-25", 45))
        val sent = SyncPlan.toUpload(rows)
        assertEquals(2, SyncPlan.syncedRows(sent, rows).size)
    }

    @Test
    fun `if either app grew mid-upload the whole day stays dirty`() {
        val sent = SyncPlan.toUpload(listOf(row("2026-09-25", 120), ytRow("2026-09-25", 45)))
        val current = listOf(row("2026-09-25", 120), ytRow("2026-09-25", 46))
        assertTrue(SyncPlan.syncedRows(sent, current).isEmpty())
    }

    @Test
    fun `a second app appearing mid-upload keeps the day dirty`() {
        val sent = SyncPlan.toUpload(listOf(row("2026-09-25", 120)))
        val current = listOf(row("2026-09-25", 120), ytRow("2026-09-25", 1))
        assertTrue(SyncPlan.syncedRows(sent, current).isEmpty())
    }
}
