package com.ekaur.android.detect

/**
 * Per-app matching rules, kept as data rather than scattered `if` statements.
 *
 * When Instagram renames its view ids -- and it will -- fixing detection should
 * mean editing the lists below, not restructuring code. The hint strings here
 * are placeholders derived from Instagram's known internal naming; the real
 * values get filled in from an on-device event dump in Phase 2.
 */
data class AppRules(
    val packageName: String,

    /** Any match means "the short-form player is on screen". */
    val playerClassHints: List<String>,
    val playerViewIdHints: List<String>,
    val playerContentHints: List<String>,

    /**
     * Any match means "definitely NOT the player" -- the main feed, DMs, a
     * profile. Checked before the positive hints so a screen that mentions both
     * is treated as not-the-player.
     */
    val notPlayerHints: List<String>,

    /**
     * How long scroll activity must be quiet before a burst is treated as one
     * completed swipe. Only used by the fallback path; index-based counting
     * ignores it.
     */
    val settleWindowMs: Long = 300,

    /** Minimum accumulated vertical movement for a burst to count as a swipe. */
    val minNetScroll: Int = 24,

    /**
     * How long the player may go quiet before the session is considered over.
     * Covers the user pausing on one video without leaving Reels.
     */
    val sessionGapMs: Long = 90_000,
) {
    fun matchesPackage(pkg: String): Boolean = pkg == packageName
}

object DetectorRules {

    /**
     * Instagram calls Reels "clips" internally, which is why the hints below
     * look for both. v1 ships Instagram only; YouTube Shorts and Facebook Reels
     * slot in as additional [AppRules] entries with no code changes.
     */
    val Instagram = AppRules(
        packageName = "com.instagram.android",
        playerClassHints = listOf(
            "clips",
            "reel",
        ),
        playerViewIdHints = listOf(
            "clips_viewer",
            "clips_video",
            "reel_viewer",
            "reels_tray",
        ),
        playerContentHints = listOf(
            "reel",
            "clips",
        ),
        notPlayerHints = listOf(
            "direct",       // DMs
            "thread",
            "profile",
            "comment",
            "explore_grid",
        ),
    )

    val all: List<AppRules> = listOf(Instagram)

    fun forPackage(pkg: String): AppRules? = all.firstOrNull { it.matchesPackage(pkg) }
}
