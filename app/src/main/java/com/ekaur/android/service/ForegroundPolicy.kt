package com.ekaur.android.service

/**
 * Decides what a foreground reading from usage stats means, before anything
 * acts on it.
 *
 * Usage stats report whichever activity came to the front last, and on some
 * phones that isn't always the app the person is looking at: MIUI's security
 * centre, Play services, a permission prompt or the keyboard can briefly come
 * out on top while YouTube is still on screen. Believing one of those as
 * "left YouTube" took the pill down in 1.5s and could switch the service off
 * 8s later -- mid-scroll. So:
 *
 * - such system screens are never taken as a destination, and
 * - leaving has to be seen on two readings in a row (about 3s) before it
 *   counts. Coming back is believed at once.
 *
 * Pure Kotlin; the service owns one and feeds it every reading.
 */
class ForegroundPolicy(
    private val transientPackages: Set<String> = DEFAULT_TRANSIENT,
    private val confirmReads: Int = 2,
) {

    enum class Reading {
        /** A counted app is in front. */
        InTracked,

        /** Somewhere else, seen enough times to be sure. */
        Left,

        /** Can't tell, or not sure yet: change nothing. */
        Unknown,
    }

    private var untrackedReads = 0

    /** Whether [packageName] is a system screen that is never a real destination. */
    fun isTransient(packageName: String, inputMethod: String?): Boolean =
        packageName in transientPackages || packageName == inputMethod

    fun read(foreground: String?, isTracked: Boolean, inputMethod: String?): Reading {
        if (foreground == null || isTransient(foreground, inputMethod)) return Reading.Unknown
        if (isTracked) {
            untrackedReads = 0
            return Reading.InTracked
        }
        untrackedReads++
        return if (untrackedReads >= confirmReads) Reading.Left else Reading.Unknown
    }

    companion object {
        val DEFAULT_TRANSIENT = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.lbe.security.miui",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
        )
    }
}
