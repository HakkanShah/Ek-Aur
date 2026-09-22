package com.ekaur.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN = 1080
private const val MARGIN = 24
private const val COLLAPSED = 168

class PillPlacementTest {

    private fun placedAt(center: Int): PillPlacement =
        PillPlacement().apply {
            settle(center)
            onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN)
        }

    @Test
    fun `collapsing returns to exactly where the pill was parked`() {
        // A message comes and goes; the window is never repositioned for it, so
        // the only call that arrives is the collapsed measure afterwards, and it
        // must land on the same centre it started from.
        val centres = listOf(
            SCREEN / 2, 300, 780, MARGIN + COLLAPSED / 2, SCREEN - MARGIN - COLLAPSED / 2,
        )
        for (center in centres) {
            val placement = placedAt(center)
            val resting = placement.restingCenter
            val before = placement.placement(SCREEN, MARGIN)

            val after = placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN)

            assertEquals("resting centre moved for $center", resting, placement.restingCenter)
            assertEquals("geometry changed for $center", before, after)
        }
    }

    @Test
    fun `ten message cycles leave the geometry untouched`() {
        val placement = placedAt(300)
        val first = placement.placement(SCREEN, MARGIN)

        repeat(10) { placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN) }

        assertEquals("the pill drifted", first, placement.placement(SCREEN, MARGIN))
    }

    @Test
    fun `a centred pill centres the window and spreads both ways`() {
        val placed = placedAt(SCREEN / 2).placement(SCREEN, MARGIN)

        // Centre of the screen -> zero offset for a CENTER_HORIZONTAL window.
        assertEquals(0, placed.offset)
        // And a message may use the whole width between the margins, symmetric.
        assertEquals(SCREEN - 2 * MARGIN, placed.availableWidth)
    }

    @Test
    fun `the window offset always tracks the resting centre`() {
        for (center in listOf(200, SCREEN / 2, 900)) {
            val placed = placedAt(center).placement(SCREEN, MARGIN)
            assertEquals(center - SCREEN / 2, placed.offset)
        }
    }

    @Test
    fun `a drag sets the new resting centre`() {
        val placement = placedAt(SCREEN / 2)

        val placed = placement.onDragEnd(300, SCREEN, MARGIN)

        assertEquals(300, placement.restingCenter)
        // And it stays there across a message.
        assertEquals(placed, placement.onCollapsedMeasure(COLLAPSED, SCREEN, MARGIN))
    }

    @Test
    fun `a drag cannot leave the pill off either edge`() {
        val placement = placedAt(SCREEN / 2)

        placement.onDragEnd(-900, SCREEN, MARGIN)
        assertEquals(MARGIN + COLLAPSED / 2, placement.restingCenter)

        placement.onDragEnd(9_000, SCREEN, MARGIN)
        assertEquals(SCREEN - MARGIN - COLLAPSED / 2, placement.restingCenter)
    }

    @Test
    fun `every resting centre keeps the pill and a symmetric message on screen`() {
        // Sweeps the whole width. The invariant that lets the window stay put:
        // whatever room the pill reports, growing symmetrically into it keeps
        // both edges inside the margins, and the collapsed pill always fits.
        for (raw in 0..SCREEN step 10) {
            val center = OverlayPlacement.clampCenter(raw, COLLAPSED, SCREEN, MARGIN)
            val avail = OverlayPlacement.symmetricWidth(center, SCREEN, MARGIN)

            assertTrue("no room for the pill itself at $raw", avail >= COLLAPSED)
            // A symmetric message's two edges.
            assertTrue("message ran off the left at $raw", center - avail / 2 >= MARGIN - 1)
            assertTrue("message ran off the right at $raw", center + avail / 2 <= SCREEN - MARGIN + 1)
            // The collapsed pill's own edges.
            assertTrue("pill ran off the left at $raw", center - COLLAPSED / 2 >= MARGIN - 1)
            assertTrue("pill ran off the right at $raw", center + COLLAPSED / 2 <= SCREEN - MARGIN + 1)
        }
    }
}
