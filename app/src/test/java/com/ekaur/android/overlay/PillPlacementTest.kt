package com.ekaur.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN = 1080
private const val MARGIN = 24
private const val COLLAPSED = 168

class PillPlacementTest {

    private fun placedAt(left: Int): PillPlacement =
        PillPlacement().apply {
            settle(left)
            onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN)
        }

    @Test
    fun `collapsing returns to exactly where the pill was parked`() {
        // The reported fault: a message moved the pill and it stayed there.
        // Nothing about an expanded pill reaches this class any more, so the
        // only way back is to the position that was never written to.
        //
        // Both halves are checked. The device screenshots caught a right-parked
        // pill, where the offset is measured from the far edge and so differs
        // from the resting left -- a rebase that a left-parked pill would hide.
        for (left in listOf(376, 700, MARGIN, SCREEN - MARGIN - COLLAPSED)) {
            val placement = placedAt(left)
            val resting = placement.restingLeft
            val before = placement.placement(SCREEN, MARGIN)

            // A message comes and goes. The window is not repositioned for it,
            // so the only call that arrives is the collapsed measure afterwards.
            val after = placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN)

            assertEquals("resting position moved from $left", resting, placement.restingLeft)
            assertEquals("geometry changed for $left", before, after)
        }
    }

    @Test
    fun `ten message cycles leave the geometry untouched`() {
        // Each cycle used to walk the pill further from where it was parked,
        // and eventually flip which way the next message opened.
        val placement = placedAt(700)
        val first = placement.placement(SCREEN, MARGIN)

        repeat(10) {
            placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN)
        }

        assertEquals("the pill drifted", first, placement.placement(SCREEN, MARGIN))
        assertTrue("a right-parked pill must stay right-anchored", first.anchorsRight)
    }

    @Test
    fun `a pill parked on the right anchors to the right edge`() {
        val left = SCREEN - MARGIN - COLLAPSED
        val placed = placedAt(left).placement(SCREEN, MARGIN)

        assertTrue(placed.anchorsRight)
        assertEquals("offset is measured from the right edge", MARGIN, placed.offset)
    }

    @Test
    fun `a pill parked on the left anchors to the left edge`() {
        val placed = placedAt(MARGIN).placement(SCREEN, MARGIN)

        assertFalse(placed.anchorsRight)
        assertEquals(MARGIN, placed.offset)
    }

    @Test
    fun `a pill against its anchored edge may use almost the whole width`() {
        val left = placedAt(SCREEN - MARGIN - COLLAPSED).placement(SCREEN, MARGIN)
        val right = placedAt(MARGIN).placement(SCREEN, MARGIN)

        assertEquals(SCREEN - 2 * MARGIN, left.availableWidth)
        assertEquals(SCREEN - 2 * MARGIN, right.availableWidth)
    }

    @Test
    fun `a drag while collapsed becomes the new resting position`() {
        val placement = placedAt(MARGIN)

        // Dragged left-anchored, out to x = 300.
        val placed = placement.onDragEnd(
            offset = 300,
            anchoredRight = false,
            viewWidth = COLLAPSED,
            screenWidth = SCREEN,
            margin = MARGIN,
        )

        assertEquals(300, placement.restingLeft)
        // And it stays there across a message.
        assertEquals(placed, placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN))
    }

    @Test
    fun `a drag while expanded keeps the pill under the edge it was dropped at`() {
        // The pill is right-anchored and a message is on screen, so the view is
        // far wider than the pill will be a moment later. The right edge is what
        // the user let go of, so that is what must be preserved.
        val placement = placedAt(SCREEN - MARGIN - COLLAPSED)
        val droppedOffset = 120

        placement.onDragEnd(
            offset = droppedOffset,
            anchoredRight = true,
            viewWidth = 700,
            screenWidth = SCREEN,
            margin = MARGIN,
        )

        assertEquals(
            "the collapsed right edge should be where the drag ended",
            SCREEN - droppedOffset,
            placement.restingLeft + COLLAPSED,
        )

        // And the following cycle is stable, rather than correcting itself.
        val settled = placement.placement(SCREEN, MARGIN)
        assertEquals(settled, placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN))
    }

    @Test
    fun `a drag cannot leave the pill off either edge`() {
        val placement = placedAt(400)

        placement.onDragEnd(-900, anchoredRight = false, viewWidth = COLLAPSED, screenWidth = SCREEN, margin = MARGIN)
        assertEquals(MARGIN, placement.restingLeft)

        placement.onDragEnd(-900, anchoredRight = true, viewWidth = COLLAPSED, screenWidth = SCREEN, margin = MARGIN)
        assertEquals(SCREEN - COLLAPSED - MARGIN, placement.restingLeft)
    }

    @Test
    fun `every resting position leaves a message inside the display`() {
        // Sweeps the whole width rather than trusting a few samples. This is the
        // invariant that lets the window stay put: whatever room the pill
        // reports, growing into it cannot cross the far margin.
        for (left in 0..(SCREEN - COLLAPSED) step 10) {
            val placed = placedAt(left).placement(SCREEN, MARGIN)

            // Where a full-width message would end, in screen coordinates: the
            // window grows inward from the edge it is anchored to.
            val farEdge = if (placed.anchorsRight) {
                SCREEN - placed.offset - placed.availableWidth
            } else {
                placed.offset + placed.availableWidth
            }

            assertTrue("offset ${placed.offset} off screen for left=$left", placed.offset >= MARGIN)
            assertEquals(
                "a message from left=$left stops short of, or crosses, the far margin",
                if (placed.anchorsRight) MARGIN else SCREEN - MARGIN,
                farEdge,
            )
            assertTrue(
                "no room left for the pill itself at left=$left",
                placed.availableWidth >= COLLAPSED,
            )
        }
    }
}
