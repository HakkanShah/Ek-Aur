package com.ekaur.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN = 1080
private const val MARGIN = 24
private const val COLLAPSED = 120

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
    fun `dragging cannot push the pill off either edge`() {
        val farLeft = OverlayPlacement.clamp(-500, COLLAPSED, SCREEN, MARGIN)
        val farRight = OverlayPlacement.clamp(SCREEN + 500, COLLAPSED, SCREEN, MARGIN)

        assertEquals(MARGIN, farLeft)
        assertEquals(SCREEN - COLLAPSED - MARGIN, farRight)
    }

    @Test
    fun `a position saved beyond the right edge comes back on screen`() {
        // An earlier build let the pill be dragged off the display entirely,
        // with no way to retrieve it. A stored position like this must never be
        // applied as-is.
        val (x, y) = OverlayPlacement.clampOrigin(
            x = SCREEN + 900,
            y = 200,
            screenWidth = SCREEN,
            screenHeight = 2400,
            margin = MARGIN,
        )

        assertTrue("x=$x still off screen", x in MARGIN..(SCREEN - MARGIN))
        assertEquals(200, y)
    }

    @Test
    fun `a position saved beyond the left edge or above the top comes back`() {
        val (x, y) = OverlayPlacement.clampOrigin(
            x = -4_000,
            y = -900,
            screenWidth = SCREEN,
            screenHeight = 2400,
            margin = MARGIN,
        )

        assertEquals(MARGIN, x)
        assertEquals(0, y)
    }

    @Test
    fun `a position saved below the bottom comes back`() {
        // What a rotation from landscape to portrait can leave behind.
        val (_, y) = OverlayPlacement.clampOrigin(
            x = 100,
            y = 5_000,
            screenWidth = SCREEN,
            screenHeight = 2400,
            margin = MARGIN,
        )

        assertTrue("y=$y below the display", y <= 2400 - MARGIN)
    }

    @Test
    fun `no stored position can land the window off screen`() {
        // Sweep well past both edges rather than checking a couple of samples.
        for (saved in -2_000..(SCREEN + 2_000) step 50) {
            val (x, _) = OverlayPlacement.clampOrigin(
                x = saved,
                y = 0,
                screenWidth = SCREEN,
                screenHeight = 2400,
                margin = MARGIN,
            )
            assertTrue("saved=$saved produced x=$x", x in MARGIN..(SCREEN - MARGIN))
        }
    }

}
