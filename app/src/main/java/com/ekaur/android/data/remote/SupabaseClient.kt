package com.ekaur.android.data.remote

import com.ekaur.android.BuildConfig
import com.ekaur.android.data.prefs.SessionStore
import com.ekaur.android.sync.DayUpload
import com.ekaur.android.sync.TokenState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the app is allowed to fail at, in terms a screen can show. */
sealed interface SyncError {
    /** No network, a timeout, or the server did not answer. */
    data object Offline : SyncError

    /** Someone already has that username. */
    data object NameTaken : SyncError

    /**
     * This device remembers an account the server no longer has.
     *
     * The token stays cryptographically valid until it expires, so nothing
     * reveals the account is gone until a foreign key to `auth.users` fails --
     * which PostgREST reports as 409, the same status as a duplicate.
     */
    data object StaleSession : SyncError

    /**
     * Anonymous sign-in is switched off for the project.
     *
     * Its own case because it is a one-switch server setting, not anything the
     * user did, and with no way to read a log off the phone "join failed" would
     * be indistinguishable from being offline.
     */
    data object SignupDisabled : SyncError

    /** Anything else, with whatever the server said. */
    data class Refused(val status: Int, val body: String) : SyncError
}

class SyncException(val error: SyncError) : Exception(error.toString())

/** One person's day, as the leaderboard shows it. */
data class LeaderboardRow(
    val userId: String,
    val username: String,
    val reelCount: Int,
    val activeMs: Long,
)

/**
 * The five calls this app makes to Supabase.
 *
 * Hand-rolled on OkHttp rather than a Supabase SDK: the surface is genuinely
 * this small, and the app had no network dependency at all before this. Every
 * request is a plain REST call against PostgREST or GoTrue.
 */
class SupabaseClient(
    private val settings: SessionStore,
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val apiKey: String = BuildConfig.SUPABASE_KEY,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val http: OkHttpClient = defaultClient(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Signs in anonymously and remembers the session.
     *
     * Anonymous because nobody types an email address to see a leaderboard, and
     * it still yields a real `auth.uid()`, which is what every row level
     * security policy is written against.
     */
    fun signInAnonymously(): String {
        val body = postObject("$baseUrl/auth/v1/signup", buildJsonObject { }, auth = false)
        return storeSession(body)
    }

    /** Returns a usable access token, refreshing first if it is close to expiry. */
    fun accessToken(): String {
        val current = settings.accessToken
        if (!TokenState.needsRefresh(current, settings.expiresAtMs, now())) return current!!

        val refresh = settings.refreshToken
            ?: throw SyncException(SyncError.Refused(401, "no refresh token"))

        val body = postObject(
            url = "$baseUrl/auth/v1/token?grant_type=refresh_token",
            payload = buildJsonObject { put("refresh_token", refresh) },
            auth = false,
        )
        storeSession(body)
        return settings.accessToken!!
    }

    /**
     * Signs in only if there is not already a session.
     *
     * The earlier version signed in on every attempt, so each failed join left
     * behind an orphan anonymous account -- two presses of the button produced
     * two accounts. Now the account is made once, on first launch, and a retry
     * reuses it.
     */
    fun ensureSignedIn(): String {
        settings.userId?.let { existing ->
            // A stored session is only useful if it can still be renewed.
            if (settings.refreshToken?.isNotBlank() == true) return existing
        }
        return signInAnonymously()
    }

    /** Whether [name] is free. One boolean over the wire, nothing else. */
    fun isUsernameAvailable(name: String): Boolean {
        val body = request(
            url = "$baseUrl/rest/v1/rpc/username_available",
            payload = buildJsonObject { put("name", name) },
            auth = true,
            prefer = null,
        )
        return (body as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
            ?: throw SyncException(SyncError.Refused(500, "unexpected availability reply: $body"))
    }

    /**
     * Claims [username] for this account.
     *
     * The availability check before this is a convenience and is always racy --
     * two people can pass it in the same second. The unique index is what
     * actually decides, so a 23505 here is an ordinary outcome, not a fault.
     */
    fun claimUsername(username: String) {
        try {
            insertProfile(username)
        } catch (e: SyncException) {
            if (e.error != SyncError.StaleSession) throw e
            // The remembered account no longer exists. Start a new one and
            // claim the name on that, rather than leaving someone stuck on a
            // screen they have no way past.
            settings.clearSession()
            signInAnonymously()
            insertProfile(username)
        }
    }

    private fun insertProfile(username: String) {
        val userId = settings.userId
            ?: throw SyncException(SyncError.Refused(401, "not signed in"))

        postArray(
            url = "$baseUrl/rest/v1/profiles",
            payload = buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", userId)
                        put("username", username)
                    }
                )
            },
            prefer = "return=representation",
        )
    }

    /** Sets whether this account appears on other people's leaderboards. */
    fun setHidden(hidden: Boolean) {
        val userId = settings.userId
            ?: throw SyncException(SyncError.Refused(401, "not signed in"))

        patch(
            url = "$baseUrl/rest/v1/profiles?id=eq.$userId",
            payload = buildJsonObject { put("hidden", hidden) },
        )
    }

    /**
     * Everyone's total for [date], biggest first.
     *
     * One call: the username is embedded through the foreign key rather than
     * fetched separately and stitched together on the device. Hidden people are
     * filtered by row level security, not by this query -- the server simply
     * does not return them.
     */
    fun leaderboard(date: String, limit: Int = 100): List<LeaderboardRow> {
        val body = get(
            "$baseUrl/rest/v1/daily_counts" +
                "?date=eq.$date" +
                "&select=user_id,reel_count,active_ms,profiles!inner(username,hidden)" +
                "&order=reel_count.desc" +
                "&limit=$limit"
        ).asArray("leaderboard")

        return body.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val profile = row["profiles"] as? JsonObject ?: return@mapNotNull null
            LeaderboardRow(
                userId = row["user_id"]?.jsonPrimitive?.content.orEmpty(),
                username = profile["username"]?.jsonPrimitive?.content.orEmpty(),
                reelCount = row["reel_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                activeMs = row["active_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }
    }

    /**
     * Upserts a batch of daily totals.
     *
     * `resolution=merge-duplicates` makes this an upsert on `(user_id, date)`,
     * so a day already on the server is replaced rather than rejected.
     */
    fun uploadDays(days: List<DayUpload>) {
        if (days.isEmpty()) return
        val userId = settings.userId
            ?: throw SyncException(SyncError.Refused(401, "not signed in"))

        postArray(
            url = "$baseUrl/rest/v1/daily_counts",
            payload = buildJsonArray {
                days.forEach { day ->
                    add(
                        buildJsonObject {
                            put("user_id", userId)
                            put("date", day.date)
                            put("reel_count", day.reelCount)
                            put("active_ms", day.activeMs)
                        }
                    )
                }
            },
            prefer = "resolution=merge-duplicates,return=representation",
        )
    }

    private fun get(url: String): JsonElement = send(
        Request.Builder().url(url).get(),
        auth = true,
        prefer = null,
    )

    private fun patch(url: String, payload: JsonObject) {
        send(
            Request.Builder().url(url).patch(payload.toString().toRequestBody(JSON_MEDIA)),
            auth = true,
            prefer = "return=minimal",
        )
    }

    private fun storeSession(body: JsonObject): String {
        val userId = body["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            ?: throw SyncException(SyncError.Refused(500, "no user in response"))
        val access = body["access_token"]?.jsonPrimitive?.content
            ?: throw SyncException(SyncError.Refused(500, "no access token"))
        val refresh = body["refresh_token"]?.jsonPrimitive?.content.orEmpty()
        val expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L

        settings.saveSession(
            userId = userId,
            accessToken = access,
            refreshToken = refresh,
            expiresAtMs = TokenState.expiryFrom(now(), expiresIn),
        )
        return userId
    }

    /**
     * A POST whose body is expected to be an object.
     *
     * Shape is asserted, never coerced. An earlier version forced anything that
     * was not an object to an empty one, so a PostgREST array reply became `{}`
     * and the next line died on a cast -- reporting failure for work the server
     * had already done.
     */
    private fun postObject(
        url: String,
        payload: Any,
        auth: Boolean = true,
        prefer: String? = null,
    ): JsonObject = request(url, payload, auth, prefer).asObject(url)

    /** A POST whose body is expected to be an array of rows. */
    private fun postArray(
        url: String,
        payload: Any,
        auth: Boolean = true,
        prefer: String? = null,
    ): JsonArray = request(url, payload, auth, prefer).asArray(url)

    private fun JsonElement.asObject(url: String): JsonObject =
        this as? JsonObject
            ?: throw SyncException(SyncError.Refused(500, "expected an object from $url, got $this"))

    private fun JsonElement.asArray(url: String): JsonArray =
        this as? JsonArray
            ?: throw SyncException(SyncError.Refused(500, "expected an array from $url, got $this"))

    private fun request(
        url: String,
        payload: Any,
        auth: Boolean,
        prefer: String?,
    ): JsonElement = send(
        Request.Builder().url(url).post(payload.toString().toRequestBody(JSON_MEDIA)),
        auth = auth,
        prefer = prefer,
    )

    private fun send(
        builder: Request.Builder,
        auth: Boolean,
        prefer: String?,
    ): JsonElement {
        builder
            .addHeader("apikey", apiKey)
            .addHeader("Content-Type", "application/json")
        if (prefer != null) builder.addHeader("Prefer", prefer)
        // The publishable key is the bearer until there is a session; after
        // that the user's own token is what RLS reads auth.uid() from.
        builder.addHeader("Authorization", "Bearer " + if (auth) accessToken() else apiKey)

        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw SyncException(SyncError.Offline)
        }

        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw SyncException(errorFor(it.code, raw))
            if (raw.isBlank()) return buildJsonObject { }
            return runCatching { json.parseToJsonElement(raw) }.getOrElse { _ ->
                throw SyncException(SyncError.Refused(it.code, "unreadable body: " + raw.take(200)))
            }
        }
    }

    /**
     * Turns a PostgREST failure into something the UI can phrase.
     *
     * The codes come from `add_friend`, which raises distinct SQLSTATEs so the
     * three outcomes a user can actually cause are told apart rather than all
     * reading "something went wrong".
     */
    private fun errorFor(status: Int, body: String): SyncError = when {
        // The unique index, not the availability check, is what decides a name
        // is taken -- so this is an ordinary outcome and gets its own case.
        // Order matters: both arrive as 409. The foreign key one means the
        // account is gone, the unique one means the name is.
        body.contains("23503") || body.contains("_id_fkey") -> SyncError.StaleSession
        body.contains("profiles_username_unique") || body.contains("23505") -> SyncError.NameTaken
        body.contains("anonymous_provider_disabled") -> SyncError.SignupDisabled
        else -> SyncError.Refused(status, body.take(300))
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Short, because every call happens either in a WorkManager job that
            // can simply run again, or behind a button someone is waiting on.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
