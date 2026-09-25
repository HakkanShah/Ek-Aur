package com.ekaur.android.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageFlipTest {

    private val screen = 2400

    @Test
    fun `a first page-sized move on the Shorts screen counts and teaches the page`() {
        val v = PageFlip.judge(net = 2100, echo = true, pageHeight = null, screenHeight = screen)
        assertEquals(1, v.flips)
        assertEquals(2100, v.learnedPageHeight)
    }

    @Test
    fun `without the Shorts screen's echo an unknown page never counts`() {
        // A feed fling that happens to travel about a screen.
        assertEquals(0, PageFlip.judge(2100, echo = false, pageHeight = null, screenHeight = screen).flips)
    }

    @Test
    fun `once the page is known a flip must match it closely`() {
        assertEquals(1, PageFlip.judge(2100, echo = true, pageHeight = 2100, screenHeight = screen).flips)
        assertEquals(1, PageFlip.judge(2090, echo = false, pageHeight = 2100, screenHeight = screen).flips)
        assertEquals(0, PageFlip.judge(1700, echo = false, pageHeight = 2100, screenHeight = screen).flips)
    }

    @Test
    fun `two quick flips merged into one burst count as two`() {
        assertEquals(2, PageFlip.judge(4200, echo = true, pageHeight = 2100, screenHeight = screen).flips)
    }

    @Test
    fun `moving back, snapping back and small moves never count`() {
        assertEquals(0, PageFlip.judge(-2100, echo = true, pageHeight = 2100, screenHeight = screen).flips)
        assertEquals(0, PageFlip.judge(0, echo = true, pageHeight = 2100, screenHeight = screen).flips)
        assertEquals(0, PageFlip.judge(288, echo = true, pageHeight = 2100, screenHeight = screen).flips)
        assertEquals(0, PageFlip.judge(288, echo = true, pageHeight = null, screenHeight = screen).flips)
    }

    @Test
    fun `a layout change is re-learned from the next Shorts flip`() {
        // Opened full screen from a link: a taller page than the one learned.
        val v = PageFlip.judge(2350, echo = true, pageHeight = 2100, screenHeight = screen)
        assertEquals(1, v.flips)
        assertEquals(2350, v.learnedPageHeight)
    }

    @Test
    fun `a flip that matches without the echo does not re-teach the page`() {
        assertNull(PageFlip.judge(2100, echo = false, pageHeight = 2100, screenHeight = screen).learnedPageHeight)
    }
}
