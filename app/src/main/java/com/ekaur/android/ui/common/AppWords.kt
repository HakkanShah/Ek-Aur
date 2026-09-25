package com.ekaur.android.ui.common

import com.ekaur.android.detect.TrackedApp

/**
 * The words that change with the apps someone counts.
 *
 * A YouTube-only person should never read "Reels today", so every label that
 * names the number goes through here.
 */
object AppWords {

    /** "Reels", "Shorts" or "Reels + Shorts". */
    fun unit(apps: Set<TrackedApp>): String {
        val insta = TrackedApp.Instagram in apps
        val yt = TrackedApp.YouTube in apps
        return when {
            insta && yt -> "Reels + Shorts"
            yt -> "Shorts"
            else -> "Reels"
        }
    }

    /** The hero's label: "Reels today", "Shorts today", "Reels + Shorts today". */
    fun today(apps: Set<TrackedApp>): String = unit(apps) + " today"

    /** "Instagram", "YouTube" or "Instagram or YouTube". */
    fun appNames(apps: Set<TrackedApp>): String {
        val ordered = TrackedApp.entries.filter { it in apps }.ifEmpty { listOf(TrackedApp.Instagram) }
        return ordered.joinToString(" or ") { it.appName }
    }

    /** "120 Reels · 45 Shorts", or null unless both apps have a count. */
    fun split(byApp: Map<TrackedApp, Int>): String? {
        val reels = byApp[TrackedApp.Instagram] ?: 0
        val shorts = byApp[TrackedApp.YouTube] ?: 0
        if (reels <= 0 || shorts <= 0) return null
        return "$reels ${if (reels == 1) "Reel" else "Reels"} · $shorts ${if (shorts == 1) "Short" else "Shorts"}"
    }
}
