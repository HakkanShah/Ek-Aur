package com.ekaur.android.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN = 1080
private const val MARGIN = 24
private const val COLLAPSED = 120

class OverlayPlacementTest {

    @Test
    fun `a centre is clamped so the pill stays fully on screen`() {
        val half = COLLAPSED / 2
        assertEquals(
            "hard left is pushed in by half the pill",
            MARGIN + half,
            OverlayPlacement.clampCenter(-500, COLLAPSED, SCREEN, MARGIN),
        )
        assertEquals(
            "hard right is pulled in by half the pill",
            SCREEN - MARGIN - half,
            OverlayPlacement.clampCenter(SCREEN + 500, COLLAPSED, SCREEN, MARGIN),
        )
        assertEquals(
            "a centre already inside is left alone",
            SCREEN / 2,
            OverlayPlacement.clampCenter(SCREEN / 2, COLLAPSED, SCREEN, MARGIN),
        )
    }

    @Test
    fun `symmetric width is the full inner width at centre`() {
        assertEquals(
            SCREEN - 2 * MARGIN,
            OverlayPlacement.symmetricWidth(SCREEN / 2, SCREEN, MARGIN),
        )
    }

    @Test
    fun `symmetric width shrinks toward an edge but never below the pill`() {
        // Parked hard against the left (centre = margin + half): the symmetric
        // room is exactly the pill's width, so the pill fits and a message wraps.
        val half = COLLAPSED / 2
        val center = OverlayPlacement.clampCenter(0, COLLAPSED, SCREEN, MARGIN)
        val width = OverlayPlacement.symmetricWidth(center, SCREEN, MARGIN)

        assertEquals(2 * half, width)
        assertTrue("the resting pill must always fit", width >= COLLAPSED - 1)
    }

    @Test
    fun `the centre offset places a CENTER_HORIZONTAL window on the centre`() {
        assertEquals(0, OverlayPlacement.centerOffset(SCREEN / 2, SCREEN))
        assertEquals(-200, OverlayPlacement.centerOffset(SCREEN / 2 - 200, SCREEN))
        assertEquals(200, OverlayPlacement.centerOffset(SCREEN / 2 + 200, SCREEN))
    }

    @Test
    fun `a centre saved off either edge comes back on screen`() {
        val (right, _) = OverlayPlacement.clampOriginCenter(
            center = SCREEN + 900, y = 200, screenWidth = SCREEN, screenHeight = 2400, margin = MARGIN,
        )
        val (left, _) = OverlayPlacement.clampOriginCenter(
            center = -4_000, y = 200, screenWidth = SCREEN, screenHeight = 2400, margin = MARGIN,
        )

        assertTrue("right=$right off screen", right in MARGIN..(SCREEN - MARGIN))
        assertEquals(MARGIN, left)
    }

    @Test
    fun `a y saved beyond the top or bottom comes back`() {
        val (_, top) = OverlayPlacement.clampOriginCenter(
            center = 100, y = -900, screenWidth = SCREEN, screenHeight = 2400, margin = MARGIN,
        )
        val (_, bottom) = OverlayPlacement.clampOriginCenter(
            center = 100, y = 5_000, screenWidth = SCREEN, screenHeight = 2400, margin = MARGIN,
        )

        assertEquals(0, top)
        assertTrue("y=$bottom below the display", bottom <= 2400 - MARGIN)
    }

    @Test
    fun `no stored centre can land the window off screen`() {
        for (saved in -2_000..(SCREEN + 2_000) step 50) {
            val (cx, _) = OverlayPlacement.clampOriginCenter(
                center = saved, y = 0, screenWidth = SCREEN, screenHeight = 2400, margin = MARGIN,
            )
            assertTrue("saved=$saved produced cx=$cx", cx in MARGIN..(SCREEN - MARGIN))
        }
    }
}
