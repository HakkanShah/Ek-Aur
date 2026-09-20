package com.ekaur.android.data.prefs

/**
 * The session the network client needs, and nothing more.
 *
 * Narrow on purpose: the client has no business with SharedPreferences, and
 * depending on the whole settings object would make every request untestable
 * without an Android context.
 */
interface SessionStore {
    val userId: String?
    val accessToken: String?
    val refreshToken: String?
    val expiresAtMs: Long?

    fun saveSession(userId: String, accessToken: String, refreshToken: String, expiresAtMs: Long)

    /** Forgets the session, so the next call signs in from scratch. */
    fun clearSession()
}
