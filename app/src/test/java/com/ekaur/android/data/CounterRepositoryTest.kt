package com.ekaur.android.data

import androidx.room.Room
import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.data.repo.DayClock
import com.ekaur.android.detect.DetectionEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

private const val IG = "com.instagram.android"

/** Kolkata, because that is where the day boundary actually matters here. */
private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

private fun reel(atMs: Long) = DetectionEvent.ReelScrolled(IG, atMs)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CounterRepositoryTest {

    private lateinit var db: EkAurDatabase
    private lateinit var repo: CounterRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            EkAurDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = CounterRepository(db, DayClock(ZONE))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `reels and shorts on one day are kept apart and summed for the total`() = runTest {
        val yt = com.ekaur.android.detect.TrackedApp.YouTube.packageName
        repeat(4) { i -> repo.onReelScrolled(reel(at(2026, 9, 25, 14, i))) }
        repeat(3) { i -> repo.onReelScrolled(DetectionEvent.ReelScrolled(yt, at(2026, 9, 25, 15, i))) }

        val rows = db.dailyCounts().forDates(listOf("2026-09-25"))
        assertEquals(2, rows.size)
        assertEquals(4, rows.single { it.packageName == IG }.reelCount)
        assertEquals(3, rows.single { it.packageName == yt }.reelCount)

        val byApp = repo.observeTodayByApp(at(2026, 9, 25, 16)).first()
        assertEquals(4, byApp[com.ekaur.android.detect.TrackedApp.Instagram])
        assertEquals(3, byApp[com.ekaur.android.detect.TrackedApp.YouTube])
        assertEquals(7, repo.observeTodayCount(at(2026, 9, 25, 16)).first())
    }

    @Test
    fun `an app with nothing today reads as zero, not missing`() = runTest {
        repeat(2) { i -> repo.onReelScrolled(reel(at(2026, 9, 25, 14, i))) }
        val byApp = repo.observeTodayByApp(at(2026, 9, 25, 16)).first()
        assertEquals(0, byApp[com.ekaur.android.detect.TrackedApp.YouTube])
    }

    @Test
    fun `reels on one day accumulate into a single row`() = runTest {
        repeat(10) { i -> repo.onReelScrolled(reel(at(2026, 9, 20, 14, i))) }

        val rows = db.dailyCounts().forDate("2026-09-20")
        assertEquals(1, rows.size)
        assertEquals(10, rows.single().reelCount)
        assertEquals(IG, rows.single().packageName)
    }

    @Test
    fun `counts land in the hour they happened`() = runTest {
        repeat(3) { repo.onReelScrolled(reel(at(2026, 9, 20, 2, 30))) }
        repeat(5) { repo.onReelScrolled(reel(at(2026, 9, 20, 23, 10))) }

        val hours = db.hourlyCounts().forDate("2026-09-20").associate { it.hour to it.reelCount }
        assertEquals(mapOf(2 to 3, 23 to 5), hours)
    }

    @Test
    fun `local midnight starts a new day`() = runTest {
        // 23:59 belongs to the 20th; one minute later belongs to the 21st.
        repo.onReelScrolled(reel(at(2026, 9, 20, 23, 59)))
        repo.onReelScrolled(reel(at(2026, 9, 21, 0, 0)))

        assertEquals(1, db.dailyCounts().forDate("2026-09-20").single().reelCount)
        assertEquals(1, db.dailyCounts().forDate("2026-09-21").single().reelCount)
    }

    @Test
    fun `today's total is observable and updates as reels arrive`() = runTest {
        val now = at(2026, 9, 20, 18, 0)
        assertEquals(0, repo.observeTodayCount(now).first())

        repeat(4) { repo.onReelScrolled(reel(at(2026, 9, 20, 18, it))) }

        assertEquals(4, repo.observeTodayCount(now).first())
    }

    @Test
    fun `a session is persisted with its duration`() = runTest {
        val start = at(2026, 9, 20, 21, 0)
        val end = at(2026, 9, 20, 21, 34)
        repo.onSessionEnded(DetectionEvent.SessionEnded(IG, start, end, reelCount = 42))

        val session = db.sessions().all().single()
        assertEquals(42, session.reelCount)
        assertEquals(34 * 60_000L, session.durationMs)

        // Time is attributed to the day the session started on.
        assertEquals(34 * 60_000L, db.dailyCounts().forDate("2026-09-20").single().activeMs)
    }

    @Test
    fun `a session running past midnight stays on the day it started`() = runTest {
        val start = at(2026, 9, 20, 23, 40)
        val end = at(2026, 9, 21, 0, 30)
        repo.onSessionEnded(DetectionEvent.SessionEnded(IG, start, end, reelCount = 60))

        assertEquals(50 * 60_000L, db.dailyCounts().forDate("2026-09-20").single().activeMs)
        assertTrue(db.dailyCounts().forDate("2026-09-21").isEmpty())
    }

    @Test
    fun `raw events older than the retention window are pruned`() = runTest {
        val now = at(2026, 9, 20, 12, 0)
        val old = now - 8L * 24 * 60 * 60 * 1000
        val recent = now - 2L * 24 * 60 * 60 * 1000

        repo.onReelScrolled(reel(old))
        repo.onReelScrolled(reel(recent))
        assertEquals(2, db.scrollEvents().count())

        val removed = repo.pruneRawEvents(now)

        assertEquals(1, removed)
        assertEquals(1, db.scrollEvents().count())
    }

    @Test
    fun `pruning raw events leaves the aggregates untouched`() = runTest {
        val now = at(2026, 9, 20, 12, 0)
        val old = now - 30L * 24 * 60 * 60 * 1000
        repeat(5) { repo.onReelScrolled(reel(old)) }

        repo.pruneRawEvents(now)

        assertEquals(0, db.scrollEvents().count())
        // The day's total is the product; it must outlive the debug log.
        val oldDate = DayClock(ZONE).dateOf(old)
        assertEquals(5, db.dailyCounts().forDate(oldDate).single().reelCount)
    }

    @Test
    fun `new counts are marked dirty for a later upload`() = runTest {
        repo.onReelScrolled(reel(at(2026, 9, 20, 9, 0)))

        assertTrue(db.dailyCounts().forDate("2026-09-20").single().dirty)
    }

    @Test
    fun `a fired milestone is remembered for the rest of the day`() = runTest {
        // The engine's once-a-day rule is only as good as this. The service is
        // restarted often enough -- force stops, OEM battery killers -- that an
        // in-memory record would replay the same milestone several times a day.
        repo.markFired("2026-09-20", "reels_100", at(2026, 9, 20, 22, 0))

        assertEquals(setOf("reels_100"), repo.firedOn("2026-09-20"))
    }

    @Test
    fun `firing the same milestone twice leaves one record`() = runTest {
        val date = "2026-09-20"
        repo.markFired(date, "night_3am", at(2026, 9, 20, 3, 0))
        repo.markFired(date, "night_3am", at(2026, 9, 20, 3, 5))

        assertEquals(setOf("night_3am"), repo.firedOn(date))
    }

    @Test
    fun `tomorrow starts with every milestone available again`() = runTest {
        repo.markFired("2026-09-20", "reels_100", at(2026, 9, 20, 22, 0))

        assertEquals(emptySet<String>(), repo.firedOn("2026-09-21"))
    }
}
