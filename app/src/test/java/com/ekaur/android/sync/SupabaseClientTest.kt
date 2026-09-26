package com.ekaur.android.sync

import com.ekaur.android.data.remote.SupabaseClient
import com.ekaur.android.data.remote.SyncError
import com.ekaur.android.data.remote.SyncException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The client against a server that answers exactly like PostgREST and GoTrue.
 *
 * Every case here is one that actually went wrong on a device, or would have.
 * There is no logcat on that phone, so anything not pinned down here gets
 * diagnosed by guesswork and a reinstall.
 */
class SupabaseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettings
    private lateinit var client: SupabaseClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        settings = FakeSettings()
        client = SupabaseClient(
            settings = settings,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKey = "test-key",
            now = { 1_000_000L },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun session() = MockResponse()
        .setResponseCode(200)
        .setBody(
            """{"access_token":"at","refresh_token":"rt","expires_in":3600,
               "user":{"id":"user-1"}}"""
        )

    @Test
    fun `claiming a username reads the array PostgREST actually returns`() {
        // The bug that shipped in build 14. PostgREST answers
        // `return=representation` with an ARRAY; the client coerced anything
        // that was not an object to {} and then died casting it -- reporting
        // failure for an insert the server had already committed.
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""[{"id":"user-1","username":"hakkan","hidden":false}]""")
        )

        client.claimUsername("hakkan")

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a name someone already has is reported as taken, not as a crash`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"code":"23505","message":"duplicate key value violates unique constraint \"profiles_username_unique\""}"""
            )
        )

        val thrown = runCatching { client.claimUsername("hakkan") }.exceptionOrNull()

        assertEquals(SyncError.NameTaken, (thrown as SyncException).error)
    }

    @Test
    fun `a stored session is reused rather than signing in again`() {
        // Each extra sign-in used to mint an orphan anonymous account -- two
        // presses of the button produced two accounts on the real server.
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        assertEquals("user-1", client.ensureSignedIn())
        assertEquals("no request should have been made", 0, server.requestCount)
    }

    @Test
    fun `with no session at all it signs in once`() {
        server.enqueue(session())

        assertEquals("user-1", client.ensureSignedIn())
        assertEquals(1, server.requestCount)
        assertEquals("user-1", settings.userId)
    }

    @Test
    fun `an expiring token is refreshed before the call that needs it`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "old"
        // Inside the skew window, so it must renew rather than risk a 401.
        settings.expiresAtMs = 1_000_000L + TokenState.SKEW_MS - 1

        server.enqueue(session())
        server.enqueue(MockResponse().setResponseCode(200).setBody("true"))

        assertTrue(client.isUsernameAvailable("hakkan"))
        assertEquals("a refresh then the call", 2, server.requestCount)
        assertEquals("at", settings.accessToken)
    }

    @Test
    fun `availability reads the bare boolean the function returns`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(MockResponse().setResponseCode(200).setBody("false"))

        assertEquals(false, client.isUsernameAvailable("hakkan"))
    }

    @Test
    fun `a body that is not JSON raises a real error, never a cast failure`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>gateway</html>"))

        val thrown = runCatching { client.isUsernameAvailable("hakkan") }.exceptionOrNull()

        assertTrue("got $thrown", thrown is SyncException)
    }

    @Test
    fun `the leaderboard flattens the embedded profile`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[
                  {"user_id":"u2","reel_count":247,"active_ms":900000,
                   "profiles":{"username":"rohit.99","hidden":false}},
                  {"user_id":"u1","reel_count":91,"active_ms":600000,
                   "profiles":{"username":"hakkan","hidden":false}}
                ]"""
            )
        )

        val rows = client.leaderboard("2026-09-20")

        assertEquals(listOf("rohit.99", "hakkan"), rows.map { it.username })
        assertEquals(listOf(247, 91), rows.map { it.reelCount })
    }

    @Test
    fun `the leaderboard reads the reels and shorts split, and old rows as all reels`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[
                  {"user_id":"u2","reel_count":165,"reels_count":120,"shorts_count":45,"active_ms":1,
                   "profiles":{"username":"mixed","hidden":false}},
                  {"user_id":"u3","reel_count":91,"reels_count":0,"shorts_count":0,"active_ms":1,
                   "profiles":{"username":"old.app","hidden":false}},
                  {"user_id":"u4","reel_count":30,"active_ms":1,
                   "profiles":{"username":"no.columns","hidden":false}}
                ]"""
            )
        )

        val rows = client.leaderboard("2026-09-25")

        assertEquals(listOf(120, 91, 30), rows.map { it.reelsCount })
        assertEquals(listOf(45, 0, 0), rows.map { it.shortsCount })
        val asked = server.takeRequest().path.orEmpty()
        assertTrue(asked, asked.contains("reels_count") && asked.contains("shorts_count"))
    }

    @Test
    fun `an upload carries the day's total and its split`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L
        server.enqueue(MockResponse().setResponseCode(201).setBody("[]"))

        client.uploadDays(listOf(DayUpload("2026-09-25", 165, 900_000, reelsCount = 120, shortsCount = 45)))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"reel_count\":165"))
        assertTrue(body, body.contains("\"reels_count\":120"))
        assertTrue(body, body.contains("\"shorts_count\":45"))
    }

    @Test
    fun `an account deleted server-side is recovered from, not reported as 409`() {
        // Exactly what happened on the device: the phone held a session for an
        // account that had been deleted. The token was still valid, so nothing
        // revealed it until the foreign key to auth.users failed -- which
        // PostgREST reports as 409, the same status as a duplicate name.
        settings.userId = "ghost"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"code":"23503","message":"violates foreign key constraint \"profiles_id_fkey\""}"""
            )
        )
        server.enqueue(session())
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""[{"username":"hakkan"}]""")
        )

        client.claimUsername("hakkan")

        assertEquals("failed insert, fresh sign-in, retry", 3, server.requestCount)
        assertEquals("user-1", settings.userId)
    }

    @Test
    fun `a name genuinely taken is not mistaken for a dead account`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"code":"23505","message":"duplicate key value violates unique constraint \"profiles_username_unique\""}"""
            )
        )

        val thrown = runCatching { client.claimUsername("hakkan") }.exceptionOrNull()

        assertEquals(SyncError.NameTaken, (thrown as SyncException).error)
        assertEquals("must not have signed in again", 1, server.requestCount)
    }

    @Test
    fun `recovery returns the old username when the device is known`() {
        settings.userId = "fresh"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(MockResponse().setResponseCode(200).setBody("\"olduser\""))

        assertEquals("olduser", client.recoverAccount("device-abc", null))
    }

    @Test
    fun `an unknown device is not an error, just nobody`() {
        // The ordinary case for a genuinely new user, so it must not throw.
        settings.userId = "fresh"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(MockResponse().setResponseCode(200).setBody("null"))

        assertEquals(null, client.recoverAccount("device-unknown", null))
    }

    @Test
    fun `with neither a device key nor a code, nothing is asked`() {
        assertEquals(null, client.recoverAccount(null, null))
        assertEquals(null, client.recoverAccount(null, "  "))
        assertEquals("no request should have been made", 0, server.requestCount)
    }

    @Test
    fun `a rename inside the cooldown reports the days left`() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"code":"P0001","message":"cooldown: 9 days left"}""")
        )

        val thrown = runCatching { client.changeUsername("newname") }.exceptionOrNull()

        assertEquals(SyncError.Cooldown(9), (thrown as SyncException).error)
    }

    @Test
    fun `an uploaded picture gets a fresh version stamp`() {
        // The file keeps one name for ever, so without a new stamp every other
        // phone would go on showing the copy it already cached.
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"Key":"avatars/user-1.webp"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("1789999999"))

        assertEquals(1789999999L, client.uploadAvatar(ByteArray(2048)))

        val upload = server.takeRequest()
        assertTrue("goes to its own path", upload.path!!.endsWith("/avatars/user-1.webp"))
        assertEquals("image/webp", upload.getHeader("Content-Type"))
        assertEquals("replaces rather than duplicates", "true", upload.getHeader("x-upsert"))
    }

    @Test
    fun `anonymous sign-in being switched off is its own diagnosis`() {
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"code":422,"error_code":"anonymous_provider_disabled"}""")
        )

        val thrown = runCatching { client.ensureSignedIn() }.exceptionOrNull()

        assertEquals(SyncError.SignupDisabled, (thrown as SyncException).error)
    }

    private fun signedIn() {
        settings.userId = "user-1"
        settings.refreshToken = "rt"
        settings.accessToken = "at"
        settings.expiresAtMs = 9_000_000L
    }

    @Test
    fun `a recovered account reads back its own profile and picture location`() {
        signedIn()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"username":"hakkan","hidden":false,"avatar_version":1790455567,"avatar_key":"old-id"}]"""
            )
        )
        val profile = client.myProfile()!!
        assertEquals("hakkan", profile.username)
        assertEquals(1790455567L, profile.avatarVersion)
        assertEquals("old-id", profile.avatarKey)
        assertTrue(server.takeRequest().path!!.contains("id=eq.user-1"))
    }

    @Test
    fun `history comes back day by day, split and all`() {
        signedIn()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"date":"2026-09-26","reel_count":51,"reels_count":0,"shorts_count":51,"active_ms":1200},
                   {"date":"2026-09-20","reel_count":700,"reels_count":0,"shorts_count":0,"active_ms":null}]"""
            )
        )
        val days = client.myDays()
        assertEquals(2, days.size)
        assertEquals(ServerDay("2026-09-26", 51, 0, 51, 1200), days[0])
        assertEquals(ServerDay("2026-09-20", 700, 0, 0, 0), days[1])
        assertTrue(server.takeRequest().path!!.contains("user_id=eq.user-1"))
    }

    @Test
    fun `leaderboard rows carry where a recovered picture lives`() {
        signedIn()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"user_id":"new-id","reel_count":5,"reels_count":5,"shorts_count":0,"active_ms":0,
                    "profiles":{"username":"hakkan","hidden":false,"avatar_version":7,"avatar_key":"old-id"}}]"""
            )
        )
        val row = client.leaderboard("2026-09-26").single()
        assertEquals("old-id", row.avatarKey)
        assertEquals("new-id", row.userId)
    }
}
