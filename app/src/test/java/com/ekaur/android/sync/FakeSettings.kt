package com.ekaur.android.sync

import com.ekaur.android.data.prefs.SessionStore

/** A session in memory, so the client can be exercised without a device. */
class FakeSettings : SessionStore {
    override var userId: String? = null
    override var accessToken: String? = null
    override var refreshToken: String? = null
    override var expiresAtMs: Long? = null

    override fun clearSession() {
        userId = null
        accessToken = null
        refreshToken = null
        expiresAtMs = null
    }

    override fun saveSession(
        userId: String,
        accessToken: String,
        refreshToken: String,
        expiresAtMs: Long,
    ) {
        this.userId = userId
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.expiresAtMs = expiresAtMs
    }
}
