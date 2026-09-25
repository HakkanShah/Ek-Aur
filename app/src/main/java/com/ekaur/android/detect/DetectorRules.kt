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
     * Views that look exactly like the player but are not it.
     *
     * Instagram's top-level tab strip is also a one-item-at-a-time pager, so it
     * reports `fromIndex == toIndex` just like Reels does. Swiping between tabs
     * would otherwise advance its position and count as reels. Checked before
     * the shape rule.
     */
    val nonPlayerViewIdHints: List<String>,

    /**
     * How long scroll activity must be quiet before a burst counts as one swipe.
     * Only used when an event reports no positions at all.
     */
    val settleWindowMs: Long = 300,

    /** Minimum accumulated vertical movement for a burst to count as a swipe. */
    val minNetScroll: Int = 24,

    /** How long the player may go quiet before the session is considered over. */
    val sessionGapMs: Long = 90_000,

    /**
     * How long a list scroll must go without any player scroll before it is
     * believed to mean "left the player".
     *
     * Instagram fires scrolls from an unrelated list *while Reels is open* --
     * measured on device arriving about 100ms after a player scroll. Treating
     * those as leaving made the state flip constantly, which made the floating
     * counter blink. Genuinely leaving Reels showed a multi-second gap, so a
     * grace window separates the two cleanly.
     */
    val playerExitGraceMs: Long = 1_500,

    /**
     * Treat a purely sideways scroll as "not the player", whatever its shape.
     *
     * Reels and Shorts are vertical feeds. The one-item pagers that only *look*
     * like them -- Instagram's top-level tabs, a YouTube channel's tab strip --
     * move horizontally. The service cannot read view ids (it has no
     * screen-reading capability), so the id denylist above never fires on a
     * phone; direction is what separates them there. Only an event that reports
     * sideways movement and no vertical movement is excluded, so a device that
     * reports no deltas at all (Android 8) falls back to the shape rule.
     */
    val verticalOnly: Boolean = true,
) {
    fun matchesPackage(pkg: String): Boolean = pkg == packageName

    /**
     * Classifies a scroll, in priority order:
     *
     * 1. A view id known to be the player -- definitely the player.
     * 2. A view id known to only look like the player -- definitely not.
     * 3. Otherwise judge by shape: one visible item is a snapping pager, several
     *    is an ordinary list.
     *
     * Falling through to shape means an Instagram rename degrades detection
     * rather than killing it, while the denylist closes the one case where a
     * different pager would otherwise be counted as reels.
     */
    fun shapeOf(signal: ScrollSignal): ScrollShape {
        if (signal.kind != ScrollSignal.Kind.ViewScrolled) return ScrollShape.Unknown

        val id = signal.viewId?.lowercase()
        if (id != null) {
            if (nonPlayerViewIdHints.any { id.contains(it.lowercase()) }) return ScrollShape.List
            if (playerViewIdHints.any { id.contains(it.lowercase()) }) return ScrollShape.Player
        }

        // A sideways-only scroll is a tab strip or carousel, never the player.
        if (verticalOnly && signal.scrollDeltaX != 0 && signal.scrollDeltaY == 0) return ScrollShape.List

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
        // Instagram's top-level tab strip: a five-page pager showing one tab at
        // a time, so structurally indistinguishable from the Reels player.
        // Seen in a device dump as swipeable_tab_view_pager with itemCount=5.
        nonPlayerViewIdHints = listOf(
            "swipeable_tab_view_pager",
        ),
    )

    /**
     * YouTube Shorts: a vertical, one-video-at-a-time feed, the same shape as
     * Reels. YouTube's player is a snapping pager (ViewPager2 or a paged
     * RecyclerView), which reports one visible item once a swipe settles; the
     * home feed, the comments and the watch-page lists all show several. The
     * ids are corroboration for a build that can read them; the shape and the
     * direction guard do the work on a phone.
     */
    val YouTube = AppRules(
        packageName = "com.google.android.youtube",
        playerViewIdHints = listOf(
            "reel_recycler",
            "reel_player",
            "shorts_player",
        ),
        playerClassHints = listOf(
            "viewpager",
        ),
        nonPlayerViewIdHints = emptyList(),
    )

    val all: List<AppRules> = listOf(Instagram, YouTube)

    fun forPackage(pkg: String): AppRules? = all.firstOrNull { it.matchesPackage(pkg) }
}
