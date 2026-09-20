package com.ekaur.android.sync

/**
 * When a session needs renewing.
 *
 * Pure arithmetic so the awkward cases are settled on the JVM rather than
 * discovered on a phone with no debugger attached: a token is refreshed
 * *before* it expires, not after a request comes back 401, and anything missing
 * or malformed counts as expired rather than valid.
 */
object TokenState {

    /** Renew this far ahead of expiry, to cover a slow request and clock drift. */
    const val SKEW_MS = 60_000L

    /**
     * True when there is no usable access token for [nowMs].
     *
     * [expiresAtMs] of null means nothing has been stored yet.
     */
    fun needsRefresh(
        accessToken: String?,
        expiresAtMs: Long?,
        nowMs: Long,
        skewMs: Long = SKEW_MS,
    ): Boolean {
        if (accessToken.isNullOrBlank()) return true
        if (expiresAtMs == null) return true
        return nowMs >= expiresAtMs - skewMs
    }

    /**
     * When a session that lasts [expiresInSeconds] from [nowMs] runs out.
     *
     * Supabase reports a lifetime, not a deadline, so the deadline is computed
     * once at sign-in rather than recomputed from a drifting clock later.
     */
    fun expiryFrom(nowMs: Long, expiresInSeconds: Long): Long =
        nowMs + expiresInSeconds * 1000L
}
