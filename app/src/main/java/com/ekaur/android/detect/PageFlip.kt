package com.ekaur.android.detect

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Counting a pager that never says which page it is on.
 *
 * YouTube's Shorts player is a RecyclerView with its own layout manager, and
 * that layout manager fills in no adapter positions: every scroll event it
 * sends has `fromIndex = toIndex = -1`. So the position rule that counts
 * Instagram (one visible item, then the next) has nothing to read.
 *
 * What it does report is distance. A device dump (realme RMX3151, 15 Shorts)
 * showed every swipe to the next Short moving the list by exactly one page --
 * 2100px there, split over two or three events about 100ms apart:
 *
 *     +1275, +820, +5   = 2100   next Short
 *     +776, +1138, +186 = 2100   next Short
 *     -1508, -592       = -2100  back to the previous one
 *     +44, -39, -5      = 0      a drag that snapped back
 *     +288 / -288                something inside the Short, not a swipe
 *
 * A snapping pager can only ever come to rest a whole number of pages away,
 * so "the burst moved exactly one page forward" is a precise test. A feed
 * fling lands wherever it lands; the chance of it stopping within a few pixels
 * of the page height is small.
 *
 * Every Shorts swipe also scrolls two container views alongside the list
 * (a ViewGroup and a FrameLayout, same moment, their movement cancelling out).
 * That "echo" is what marks a burst as coming from the Shorts screen at all,
 * before the page height is known.
 */
object PageFlip {

    /** A settled burst's verdict. */
    data class Verdict(
        /** How many pages forward it moved; 0 for none, or for moving back. */
        val flips: Int,
        /** The page height this burst proves, to remember for next time. */
        val learnedPageHeight: Int? = null,
    )

    /** Most pages a single burst may claim, as with positions. */
    const val MAX_FLIPS_PER_BURST = 3

    /**
     * @param net total movement of the pager in the burst; forward is positive.
     * @param echo whether the Shorts containers scrolled with it.
     * @param pageHeight the page height learned so far, if any.
     * @param screenHeight the display height, to recognise a first page.
     */
    fun judge(net: Int, echo: Boolean, pageHeight: Int?, screenHeight: Int): Verdict {
        if (net <= 0) return Verdict(0)

        if (pageHeight != null && pageHeight > 0) {
            val pages = (net.toFloat() / pageHeight).roundToInt()
            if (pages in 1..MAX_FLIPS_PER_BURST &&
                abs(net - pages * pageHeight) <= tolerance(pages * pageHeight)
            ) {
                return Verdict(pages, learnedPageHeight = if (pages == 1 && echo) net else null)
            }
        }

        // Nothing learned yet, or the layout changed (rotation, split screen,
        // Shorts opened full screen from a link): a whole-page-sized move on
        // the Shorts screen is a flip, and teaches the page height.
        if (echo && net >= screenHeight * MIN_PAGE_OF_SCREEN && net <= screenHeight * MAX_PAGE_OF_SCREEN) {
            return Verdict(1, learnedPageHeight = net)
        }
        return Verdict(0)
    }

    private fun tolerance(expected: Int): Int = maxOf(24, (expected * 0.03f).toInt())

    // The page is the screen minus whatever YouTube keeps around it (status
    // bar, its bottom tabs): 2100 of 2400 on the dump's phone.
    private const val MIN_PAGE_OF_SCREEN = 0.5f
    private const val MAX_PAGE_OF_SCREEN = 1.2f
}
