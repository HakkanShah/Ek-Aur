package com.ekaur.android.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the leaderboard is switched on, and the identity behind it.
 *
 * Off is the default and means *off*: no account is created, no request is
 * made, nothing leaves the phone. The README promises that, so it is enforced
 * here rather than trusted to a screen somewhere.
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _joined = MutableStateFlow(prefs.getBoolean(KEY_JOINED, false))

    /** True once the user has explicitly joined the leaderboard. */
    val joined: StateFlow<Boolean> = _joined.asStateFlow()

    val displayName: String? get() = prefs.getString(KEY_NAME, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val friendCode: String? get() = prefs.getString(KEY_FRIEND_CODE, null)
    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)

    val expiresAtMs: Long?
        get() = prefs.getLong(KEY_EXPIRES, -1L).takeIf { it > 0 }

    fun saveSession(userId: String, accessToken: String, refreshToken: String, expiresAtMs: Long) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putLong(KEY_EXPIRES, expiresAtMs)
            .apply()
    }

    fun saveProfile(displayName: String, friendCode: String) {
        prefs.edit()
            .putString(KEY_NAME, displayName)
            .putString(KEY_FRIEND_CODE, friendCode)
            .apply()
    }

    fun setJoined(value: Boolean) {
        prefs.edit().putBoolean(KEY_JOINED, value).apply()
        _joined.value = value
    }

    /**
     * Forgets everything about the account.
     *
     * Leaving the leaderboard has to be as complete as never having joined, or
     * the switch is a lie. The counts themselves stay -- they are the user's
     * own data and were never the server's to begin with.
     */
    fun leave() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .remove(KEY_NAME)
            .remove(KEY_FRIEND_CODE)
            .putBoolean(KEY_JOINED, false)
            .apply()
        _joined.value = false
    }

    private companion object {
        const val NAME = "settings"
        const val KEY_JOINED = "joined"
        const val KEY_NAME = "display_name"
        const val KEY_USER_ID = "user_id"
        const val KEY_FRIEND_CODE = "friend_code"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
    }
}
