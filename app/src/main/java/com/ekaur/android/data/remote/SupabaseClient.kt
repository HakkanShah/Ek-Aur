package com.ekaur.android.data.remote

import com.ekaur.android.BuildConfig
import com.ekaur.android.data.prefs.SettingsStore
import com.ekaur.android.sync.DayUpload
import com.ekaur.android.sync.TokenState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    /** The friend code does not exist. */
    data object NoSuchCode : SyncError

    /** The user pasted their own code. */
    data object OwnCode : SyncError

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

/**
 * The five calls this app makes to Supabase.
 *
 * Hand-rolled on OkHttp rather than a Supabase SDK: the surface is genuinely
 * this small, and the app had no network dependency at all before this. Every
 * request is a plain REST call against PostgREST or GoTrue.
 */
class SupabaseClient(
    private val settings: SettingsStore,
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
        val body = post("$baseUrl/auth/v1/signup", buildJsonObject { }, auth = false)
        return storeSession(body)
    }

    /** Returns a usable access token, refreshing first if it is close to expiry. */
    fun accessToken(): String {
        val current = settings.accessToken
        if (!TokenState.needsRefresh(current, settings.expiresAtMs, now())) return current!!

        val refresh = settings.refreshToken
            ?: throw SyncException(SyncError.Refused(401, "no refresh token"))

        val body = post(
            url = "$baseUrl/auth/v1/token?grant_type=refresh_token",
            payload = buildJsonObject { put("refresh_token", refresh) },
            auth = false,
        )
        storeSession(body)
        return settings.accessToken!!
    }

    /** Creates this account's profile row and returns the friend code it was given. */
    fun createProfile(displayName: String): String {
        val userId = settings.userId
            ?: throw SyncException(SyncError.Refused(401, "not signed in"))

        val rows = post(
            url = "$baseUrl/rest/v1/profiles?select=friend_code",
            payload = buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", userId)
                        put("display_name", displayName.trim())
                    }
                )
            },
            prefer = "return=representation,resolution=merge-duplicates",
        )

        return rows.jsonArray.firstOrNull()
            ?.jsonObject?.get("friend_code")?.jsonPrimitive?.content
            ?: throw SyncException(SyncError.Refused(500, "no friend code returned"))
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

        post(
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
            prefer = "resolution=merge-duplicates,return=minimal",
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

    private fun post(
        url: String,
        payload: Any,
        auth: Boolean = true,
        prefer: String? = null,
    ): JsonObject = request(url, payload, auth, prefer).let {
        if (it is JsonObject) it else buildJsonObject { }
    }

    private fun request(
        url: String,
        payload: Any,
        auth: Boolean,
        prefer: String?,
    ): Any {
        val text = when (payload) {
            is JsonObject -> payload.toString()
            is JsonArray -> payload.toString()
            else -> payload.toString()
        }

        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(text.toRequestBody(JSON_MEDIA))

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
            return json.parseToJsonElement(raw)
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
        body.contains("no such code") -> SyncError.NoSuchCode
        body.contains("your own code") -> SyncError.OwnCode
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
