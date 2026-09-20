package com.ekaur.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN = 1080
private const val MARGIN = 24
private const val COLLAPSED = 120
private const val EXPANDED = 700

class OverlayPlacementTest {

    @Test
    fun `a pill on the left half anchors left`() {
        assertFalse(OverlayPlacement.anchorsRight(x = 40, width = COLLAPSED, screenWidth = SCREEN))
    }

    @Test
    fun `a pill on the right half anchors right`() {
        val x = SCREEN - MARGIN - COLLAPSED
        assertTrue(OverlayPlacement.anchorsRight(x = x, width = COLLAPSED, screenWidth = SCREEN))
    }

    @Test
    fun `expanding on the left edge grows rightward and stays put`() {
        val left = MARGIN
        val x = OverlayPlacement.resolveX(
            collapsedLeft = left,
            collapsedRight = left + COLLAPSED,
            width = EXPANDED,
            screenWidth = SCREEN,
            anchorsRight = false,
            margin = MARGIN,
        )

        assertEquals("left edge should not move", left, x)
        assertTrue("must stay on screen", x + EXPANDED <= SCREEN - MARGIN)
    }

    @Test
    fun `expanding on the right edge grows leftward and stays visible`() {
        // The exact case reported: a pill parked hard against the right edge.
        val left = SCREEN - MARGIN - COLLAPSED
        val right = left + COLLAPSED

        val x = OverlayPlacement.resolveX(
            collapsedLeft = left,
            collapsedRight = right,
            width = EXPANDED,
            screenWidth = SCREEN,
            anchorsRight = true,
            margin = MARGIN,
        )

        assertEquals("right edge should stay pinned", right, x + EXPANDED)
        assertTrue("must not run off the left", x >= MARGIN)
        assertTrue("must not run off the right", x + EXPANDED <= SCREEN - MARGIN)
    }

    @Test
    fun `a message too wide for either side is clamped on screen`() {
        val tooWide = SCREEN + 400
        val left = SCREEN - MARGIN - COLLAPSED

        val x = OverlayPlacement.resolveX(
            collapsedLeft = left,
            collapsedRight = left + COLLAPSED,
            width = tooWide,
            screenWidth = SCREEN,
            anchorsRight = true,
            margin = MARGIN,
        )

        assertEquals("nothing sensible left to do but pin to the margin", MARGIN, x)
    }

    @Test
    fun `dragging cannot push the pill off either edge`() {
        val farLeft = OverlayPlacement.clamp(-500, COLLAPSED, SCREEN, MARGIN)
        val farRight = OverlayPlacement.clamp(SCREEN + 500, COLLAPSED, SCREEN, MARGIN)

        assertEquals(MARGIN, farLeft)
        assertEquals(SCREEN - COLLAPSED - MARGIN, farRight)
    }

    @Test
    fun `every position on screen resolves to something fully visible`() {
        // Sweep the whole width rather than trusting a couple of samples.
        for (left in 0..(SCREEN - COLLAPSED) step 10) {
            val anchors = OverlayPlacement.anchorsRight(left, COLLAPSED, SCREEN)
            val x = OverlayPlacement.resolveX(
                collapsedLeft = left,
                collapsedRight = left + COLLAPSED,
                width = EXPANDED,
                screenWidth = SCREEN,
                anchorsRight = anchors,
                margin = MARGIN,
            )
            assertTrue("x=$x off the left for left=$left", x >= MARGIN)
            assertTrue("x=$x off the right for left=$left", x + EXPANDED <= SCREEN - MARGIN)
        }
    }
}
