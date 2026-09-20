package com.ekaur.android.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This device's identity on the leaderboard.
 *
 * Everyone who uses the app is on one list, so there is no joining step -- the
 * username is what gates the app, and [hidden] is the only way off the list.
 */
class SettingsStore(context: Context) : SessionStore {

    private val prefs =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _username = MutableStateFlow(prefs.getString(KEY_NAME, null))

    /** Null until the user has picked one, which gates the whole app. */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _hidden = MutableStateFlow(prefs.getBoolean(KEY_HIDDEN, false))

    /** True when this account is kept off other people's leaderboards. */
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    /** The version of this account's picture, or null when there is none. */
    val avatarVersion: Long? get() = prefs.getLong(KEY_AVATAR, -1L).takeIf { it > 0 }

    /** Shown in setup, and the only way back on a new phone. */
    val recoveryCode: String? get() = prefs.getString(KEY_RECOVERY, null)

    override val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    override val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    override val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)

    override val expiresAtMs: Long?
        get() = prefs.getLong(KEY_EXPIRES, -1L).takeIf { it > 0 }

    override fun saveSession(userId: String, accessToken: String, refreshToken: String, expiresAtMs: Long) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putLong(KEY_EXPIRES, expiresAtMs)
            .apply()
    }

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .apply()
    }

    fun saveAvatarVersion(version: Long) {
        prefs.edit().putLong(KEY_AVATAR, version).apply()
    }

    fun saveRecoveryCode(code: String) {
        prefs.edit().putString(KEY_RECOVERY, code).apply()
    }

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_NAME, username).apply()
        _username.value = username
    }

    /** Sends the app back to the name screen. */
    fun forgetUsername() {
        prefs.edit().remove(KEY_NAME).apply()
        _username.value = null
    }

    fun setHidden(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIDDEN, value).apply()
        _hidden.value = value
    }

    /**
     * Forgets everything about the account.
     *
     * The counts themselves stay -- they are the user's own data and were never
     * the server's to begin with. Only used to recover from a session whose
     * account no longer exists.
     */
    fun leave() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .remove(KEY_NAME)
            .remove(KEY_HIDDEN)
            .remove(KEY_RECOVERY)
            .remove(KEY_AVATAR)
            .apply()
        _username.value = null
        _hidden.value = false
    }

    private companion object {
        const val NAME = "settings"
        const val KEY_NAME = "display_name"
        const val KEY_USER_ID = "user_id"
        const val KEY_HIDDEN = "hidden"
        const val KEY_RECOVERY = "recovery_code"
        const val KEY_AVATAR = "avatar_version"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
    }
}
