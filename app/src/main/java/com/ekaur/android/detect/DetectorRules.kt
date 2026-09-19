package com.ekaur.android.detect

/**
 * Per-app detection rules.
 *
 * These were rewritten after a real device dump. The first version matched
 * strings in class names, view ids and content descriptions, which failed badly:
 * Instagram's Reels screen contains words like "comment" that read as
 * not-the-player, and its main feed contains a Reels tab whose description is
 * literally "Reels". String matching pulled the detector in and out of the
 * player constantly, and every re-entry reset the counting baseline, so most
 * swipes went uncounted.
 *
 * The reliable signal turned out to be structural: how many adapter items are
 * visible after the scroll. Names can be renamed; a full-screen pager showing
 * one item at a time cannot stop being that.
 */
data class AppRules(
    val packageName: String,

    /**
     * View ids that identify the player outright. Corroboration only -- a scroll
     * is never judged by these alone. From the device dump:
     * `com.instagram.android:id/clips_viewer_view_pager`.
     */
    val playerViewIdHints: List<String>,

    /** Class names typical of a snapping pager. Also corroboration only. */
    val playerClassHints: List<String>,

    /**
     * How long scroll activity must be quiet before a burst counts as one swipe.
     * Only used when an event reports no positions at all.
     */
    val settleWindowMs: Long = 300,

    /** Minimum accumulated vertical movement for a burst to count as a swipe. */
    val minNetScroll: Int = 24,

    /** How long the player may go quiet before the session is considered over. */
    val sessionGapMs: Long = 90_000,
) {
    fun matchesPackage(pkg: String): Boolean = pkg == packageName

    /**
     * Classifies a scroll by how many items it left visible.
     *
     * One visible item means a snapping pager -- the Reels player. Several means
     * an ordinary list -- the feed, which must never count. The id and class
     * hints only raise confidence; they are not required, so detection survives
     * Instagram renaming them.
     */
    fun shapeOf(signal: ScrollSignal): ScrollShape {
        if (signal.kind != ScrollSignal.Kind.ViewScrolled) return ScrollShape.Unknown
        val from = signal.fromIndex
        val to = signal.toIndex
        if (from < 0 || to < 0) return ScrollShape.Unknown
        return if (from == to) ScrollShape.Player else ScrollShape.List
    }

    /** True when this scroll is unmistakably the player, by name as well as shape. */
    fun isNamedPlayer(signal: ScrollSignal): Boolean {
        val id = signal.viewId?.lowercase()
        val cls = signal.className?.lowercase()
        return playerViewIdHints.any { id?.contains(it.lowercase()) == true } ||
            playerClassHints.any { cls?.contains(it.lowercase()) == true }
    }
}

object DetectorRules {

    /**
     * Instagram calls Reels "clips" internally. The ids below came off a real
     * dump rather than guesswork.
     */
    val Instagram = AppRules(
        packageName = "com.instagram.android",
        playerViewIdHints = listOf(
            "clips_viewer",
            "clips_items",
        ),
        playerClassHints = listOf(
            "viewpager",
        ),
    )

    val all: List<AppRules> = listOf(Instagram)

    fun forPackage(pkg: String): AppRules? = all.firstOrNull { it.matchesPackage(pkg) }
}
