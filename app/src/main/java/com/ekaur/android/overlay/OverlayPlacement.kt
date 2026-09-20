package com.ekaur.android.overlay

/**
 * Where the floating pill sits, and which way it grows when it has something
 * to say.
 *
 * Pure arithmetic, kept out of [OverlayHost] so it can be tested without a
 * window. The window is anchored to whichever edge the pill is parked on, so it
 * grows inward on its own as a message arrives; nothing here ever computes a new
 * position for an expanding pill, because the window is never moved by one.
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
     * How wide the pill may grow before it reaches the far margin.
     *
     * [offset] is measured from the anchored edge, so this is the same
     * expression under either anchoring. Constraining the pill to this instead
     * of a fixed width is what lets the window stay where it is: a message can
     * never grow past the room it has, so nothing ever needs repositioning.
     */
    fun availableWidth(offset: Int, screenWidth: Int, margin: Int): Int =
        (screenWidth - margin - offset).coerceAtLeast(0)

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
