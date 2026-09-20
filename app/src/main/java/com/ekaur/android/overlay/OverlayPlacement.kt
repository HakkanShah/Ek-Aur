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

    /** Keeps a window of [width] fully on screen, margins included. */
    fun clamp(x: Int, width: Int, screenWidth: Int, margin: Int): Int {
        val maxX = (screenWidth - width - margin).coerceAtLeast(margin)
        return x.coerceIn(margin, maxX)
    }
}
