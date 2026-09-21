package com.ekaur.android.update

import android.content.Context

/** Remembers when we last checked and which build is already downloaded. */
class UpdatePrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_CHECK, value).apply() }

    /** The build number of the APK sitting ready in the cache, or 0 for none. */
    var downloadedVersionCode: Int
        get() = prefs.getInt(KEY_DOWNLOADED, 0)
        set(value) { prefs.edit().putInt(KEY_DOWNLOADED, value).apply() }

    /** The name of that ready build, so a launch can name it even while offline. */
    var downloadedVersionName: String?
        get() = prefs.getString(KEY_DOWNLOADED_NAME, null)
        set(value) { prefs.edit().putString(KEY_DOWNLOADED_NAME, value).apply() }

    /** Whether a found update downloads on its own in the background. */
    var autoDownload: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO, value).apply() }

    private companion object {
        const val NAME = "updates"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val KEY_DOWNLOADED = "downloaded_version_code"
        const val KEY_DOWNLOADED_NAME = "downloaded_version_name"
        const val KEY_AUTO = "auto_download"
    }
}
