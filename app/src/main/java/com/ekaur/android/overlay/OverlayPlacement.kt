package com.ekaur.android.overlay

/**
 * Where the floating pill sits, and which way it grows when it has something
 * to say.
 *
 * Pure arithmetic, kept out of [OverlayHost] so it can be tested without a
 * window. The window is laid out from its left edge, so a pill parked on the
 * right of the screen would expand straight off the display unless its x is
 * recomputed as it widens -- which is exactly what happened on device.
 */
object OverlayPlacement {

    /**
     * Which edge the pill belongs to, decided by the half of the screen its
     * centre falls in. A pill on the right grows leftward so it stays visible,
     * and keeping the anchor on the nearer edge means the pill itself does not
     * appear to move when the message opens.
     */
    fun anchorsRight(x: Int, width: Int, screenWidth: Int): Boolean =
        (x + width / 2) > screenWidth / 2

    /**
     * The x to use for a pill of [width], given where it sits when collapsed.
     *
     * Anchored right, the collapsed right edge is held and the window's left
     * edge moves out to meet the new width. Anchored left, the left edge simply
     * stays. Either way the result is clamped inside the display, which covers
     * the case where a line is too long to fit on the chosen side at all.
     */
    fun resolveX(
        collapsedLeft: Int,
        collapsedRight: Int,
        width: Int,
        screenWidth: Int,
        anchorsRight: Boolean,
        margin: Int,
    ): Int {
        val desired = if (anchorsRight) collapsedRight - width else collapsedLeft
        return clamp(desired, width, screenWidth, margin)
    }

    /**
     * Keeps a stored origin on screen before the pill has ever been measured.
     *
     * A position saved by an older build, or one that no longer fits after a
     * rotation, must not be applied as-is -- the window would be placed off the
     * display and the user would have nothing to grab. Exact placement is
     * refined once the real width is known.
     */
    fun clampOrigin(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val maxX = (screenWidth - margin).coerceAtLeast(margin)
        val maxY = (screenHeight - margin).coerceAtLeast(0)
        return x.coerceIn(margin, maxX) to y.coerceIn(0, maxY)
    }

    /** Keeps a window of [width] fully on screen, margins included. */
    fun clamp(x: Int, width: Int, screenWidth: Int, margin: Int): Int {
        val maxX = (screenWidth - width - margin).coerceAtLeast(margin)
        return x.coerceIn(margin, maxX)
    }
}
