package com.ekaur.android.sync

import androidx.room.Room
import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.prefs.SettingsStore
import com.ekaur.android.data.remote.SupabaseClient
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.data.repo.DayClock
import com.ekaur.android.detect.DetectionEvent
import com.ekaur.android.detect.TrackedApp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
private fun at(hour: Int, minute: Int = 0): Long =
    ZonedDateTime.of(2026, 9, 25, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

/**
 * The whole sync, against a server that answers like PostgREST, for the case
 * that would silently lose a count: Instagram's row for today already
 * uploaded, then Shorts scrolled later the same day. Only the YouTube row is
 * dirty, but the server keeps one row per day -- so the upload must still
 * carry Instagram's count, or the leaderboard would drop it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncerTwoAppsTest {

    private lateinit var db: EkAurDatabase
    private lateinit var server: MockWebServer
    private lateinit var settings: SettingsStore
    private lateinit var syncer: Syncer
    private lateinit var repo: CounterRepository

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, EkAurDatabase::class.java).allowMainThreadQueries().build()
        repo = CounterRepository(db, DayClock(ZONE))
        server = MockWebServer().apply { start() }
        settings = SettingsStore(app).apply {
            saveUsername("hakkan")
            saveSession("user-1", "at", "rt", 9_000_000L)
        }
        val client = SupabaseClient(
            settings = settings,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "test-key",
            now = { 1_000_000L },
        )
        syncer = Syncer(db, settings, client, now = { 5L })
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `shorts added after reels were synced still upload the day's full total`() = runTest {
        repeat(4) { repo.onReelScrolled(DetectionEvent.ReelScrolled(TrackedApp.Instagram.packageName, at(10, it))) }
        server.enqueue(MockResponse().setResponseCode(201).setBody("[]"))
        assertTrue(syncer.syncNow() is SyncResult.Uploaded)
        server.takeRequest()

        repeat(3) { repo.onReelScrolled(DetectionEvent.ReelScrolled(TrackedApp.YouTube.packageName, at(11, it))) }
        server.enqueue(MockResponse().setResponseCode(201).setBody("[]"))
        val result = syncer.syncNow()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"reel_count\":7"))
        assertTrue(body, body.contains("\"reels_count\":4"))
        assertTrue(body, body.contains("\"shorts_count\":3"))
        assertTrue(result is SyncResult.Uploaded)
        assertTrue(db.dailyCounts().dirtyRows(10).isEmpty())
    }

    @Test
    fun `a failed upload leaves both apps dirty for the retry`() = runTest {
        repeat(2) { repo.onReelScrolled(DetectionEvent.ReelScrolled(TrackedApp.Instagram.packageName, at(10, it))) }
        repeat(2) { repo.onReelScrolled(DetectionEvent.ReelScrolled(TrackedApp.YouTube.packageName, at(10, 10 + it))) }
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val result = syncer.syncNow()

        assertTrue(result is SyncResult.Failed)
        assertEquals(2, db.dailyCounts().dirtyRows(10).size)
    }
}
