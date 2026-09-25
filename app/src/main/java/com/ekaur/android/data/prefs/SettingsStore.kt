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
     * Whether Ek Aur switches itself off once the user leaves Instagram.
     *
     * On by default: a payment app blocks while any accessibility service is
     * enabled, so turning off on leaving keeps payments clean without a manual
     * step. Read straight from prefs because the accessibility service, which
     * lives in another process, reads it on its own timer rather than observing
     * a flow.
     */
    var autoOffOnLeave: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OFF, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_OFF, value).apply()
        }

    /**
     * Whether the guided setup has been walked through or skipped once.
     *
     * The wizard fronts the app while accessibility is off and this is false;
     * after that it is one tap away from Home and Setup, never in the way.
     */
    var setupWizardSeen: Boolean
        get() = prefs.getBoolean(KEY_WIZARD_SEEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WIZARD_SEEN, value).apply()
        }

    private val _countedApps = MutableStateFlow(readCountedApps())

    /**
     * Which apps are counted. Instagram is on unless switched off; YouTube
     * Shorts is off until the user turns it on (onboarding asks new users, and
     * existing users get a one-time prompt), so an update never starts
     * watching YouTube unannounced. Never empty: the last app cannot be
     * switched off.
     */
    val countedApps: StateFlow<Set<com.ekaur.android.detect.TrackedApp>> = _countedApps.asStateFlow()

    fun setCounting(app: com.ekaur.android.detect.TrackedApp, on: Boolean) {
        val next = _countedApps.value.toMutableSet().apply { if (on) add(app) else remove(app) }
        if (next.isEmpty()) return
        prefs.edit()
            .putBoolean(KEY_COUNT_PREFIX + app.name, on)
            .apply()
        _countedApps.value = next
    }

    /** Sets exactly which apps are counted, in one step. Ignored if empty. */
    fun setCountedApps(apps: Set<com.ekaur.android.detect.TrackedApp>) {
        if (apps.isEmpty()) return
        prefs.edit().apply {
            com.ekaur.android.detect.TrackedApp.entries.forEach { putBoolean(KEY_COUNT_PREFIX + it.name, it in apps) }
        }.apply()
        _countedApps.value = apps
    }

    fun isCounting(packageName: String): Boolean =
        _countedApps.value.any { it.packageName == packageName }

    private val _lookOverride = MutableStateFlow(prefs.getString(KEY_LOOK, null))

    /**
     * A look the person picked by hand ("Instagram", "Shorts", "Both"), or null
     * to follow the apps being counted. Stored by name; the UI maps it.
     */
    val lookOverride: StateFlow<String?> = _lookOverride.asStateFlow()

    fun setLookOverride(name: String?) {
        prefs.edit().apply { if (name == null) remove(KEY_LOOK) else putString(KEY_LOOK, name) }.apply()
        _lookOverride.value = name
    }

    /** Whether the user has answered "what do you scroll?" (or the Shorts prompt). */
    var appsChosen: Boolean
        get() = prefs.getBoolean(KEY_APPS_CHOSEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_APPS_CHOSEN, value).apply()
        }

    /**
     * Whether the "Count Shorts too?" card has been answered. Separate from
     * [appsChosen] because build 50 couldn't see YouTube (no package-visibility
     * query), so people who chose then were never really offered Shorts.
     */
    var shortsAsked: Boolean
        get() = prefs.getBoolean(KEY_SHORTS_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHORTS_ASKED, value).apply()
        }

    private fun readCountedApps(): Set<com.ekaur.android.detect.TrackedApp> {
        val apps = com.ekaur.android.detect.TrackedApp.entries.filter { app ->
            prefs.getBoolean(
                KEY_COUNT_PREFIX + app.name,
                app == com.ekaur.android.detect.TrackedApp.Instagram,
            )
        }.toSet()
        return apps.ifEmpty { setOf(com.ekaur.android.detect.TrackedApp.Instagram) }
    }

    /**
     * Whether the "all set" confetti has played. It is a one-time moment; a
     * permission switched off and on again later does not earn it twice.
     */
    var setupCelebrated: Boolean
        get() = prefs.getBoolean(KEY_CELEBRATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CELEBRATED, value).apply()
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
        const val KEY_AUTO_OFF = "auto_off_on_leave"
        const val KEY_WIZARD_SEEN = "setup_wizard_seen"
        const val KEY_CELEBRATED = "setup_celebrated"
        const val KEY_COUNT_PREFIX = "count_app_"
        const val KEY_APPS_CHOSEN = "apps_chosen"
        const val KEY_SHORTS_ASKED = "shorts_prompt_v2_seen"
        const val KEY_LOOK = "look_override"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
    }
}
