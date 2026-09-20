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
    fun `anonymous sign-in being switched off is its own diagnosis`() {
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"code":422,"error_code":"anonymous_provider_disabled"}""")
        )

        val thrown = runCatching { client.ensureSignedIn() }.exceptionOrNull()

        assertEquals(SyncError.SignupDisabled, (thrown as SyncException).error)
    }
}
