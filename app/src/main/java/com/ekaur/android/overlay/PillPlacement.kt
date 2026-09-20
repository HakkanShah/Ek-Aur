package com.ekaur.android.overlay

/**
 * Where the pill rests, and how much room a message has beside it.
 *
 * The resting position is authoritative state. It changes only when the stored
 * position is loaded or when a drag ends -- never as a side effect of a message
 * appearing. An earlier design re-derived it from the window's current x on
 * every measurement, which meant an expanded window overwrote the parked
 * position on the way back down and the pill never returned to it.
 *
 * Everything else is derived on demand, so no field can go stale: which edge the
 * window is anchored to, the offset from that edge, and the width a message has
 * available before it would reach the far margin.
 */
class PillPlacement {

    /** Absolute left edge of the collapsed pill. The one piece of real state. */
    var restingLeft: Int = 0
        private set

    /** Learned from the first collapsed measurement. */
    var collapsedWidth: Int = 0
        private set

    /** Applies a stored position, on show or after a reset. */
    fun settle(left: Int) {
        restingLeft = left
    }

    /**
     * Records a collapsed measurement and returns the geometry to apply.
     *
     * Only collapsed measurements reach this. An expanded pill must never
     * influence where the pill rests, which is the whole point of the class.
     */
    fun onCollapsedMeasure(width: Int, screenWidth: Int, margin: Int): Placement {
        collapsedWidth = width
        restingLeft = OverlayPlacement.clamp(restingLeft, width, screenWidth, margin)
        return placement(screenWidth, margin)
    }

    /**
     * Takes a finished drag and makes it the new resting position.
     *
     * [offset] is measured from whichever edge the window is currently anchored
     * to, so it is converted back to an absolute left edge first. Because the
     * anchored edge is what stays fixed, a drag performed while a message is on
     * screen leaves the collapsed pill under the edge it was dropped at, rather
     * than leaping across once the message goes.
     */
    fun onDragEnd(
        offset: Int,
        anchoredRight: Boolean,
        viewWidth: Int,
        screenWidth: Int,
        margin: Int,
    ): Placement {
        val width = if (collapsedWidth > 0) collapsedWidth else viewWidth
        collapsedWidth = width
        val left = if (anchoredRight) screenWidth - offset - width else offset
        restingLeft = OverlayPlacement.clamp(left, width, screenWidth, margin)
        return placement(screenWidth, margin)
    }

    /** The geometry the current resting position implies. */
    fun placement(screenWidth: Int, margin: Int): Placement {
        val width = collapsedWidth.coerceAtLeast(1)
        val anchorsRight = OverlayPlacement.anchorsRight(restingLeft, width, screenWidth)
        val offset =
            if (anchorsRight) screenWidth - (restingLeft + width) else restingLeft
        return Placement(
            anchorsRight = anchorsRight,
            offset = offset,
            availableWidth = OverlayPlacement.availableWidth(offset, screenWidth, margin),
        )
    }
}

/**
 * How the overlay window should be placed.
 *
 * [offset] is relative to the anchored edge, so the window manager grows the
 * window inward by itself when a message arrives -- no reposition, and nothing
 * that could move the pill away from where it was parked.
 */
data class Placement(
    val anchorsRight: Boolean,
    val offset: Int,
    val availableWidth: Int,
)
