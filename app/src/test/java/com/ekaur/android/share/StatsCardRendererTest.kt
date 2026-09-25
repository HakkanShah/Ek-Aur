package com.ekaur.android.share

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun stats(reels: Int = 969, week: List<Int> = listOf(12, 40, 0, 120, 88, 260, 969)) =
    CardStats(
        username = "hakkan",
        reelsToday = reels,
        activeMsToday = 112 * 60_000L,
        week = week,
        bestEver = 969,
        peakHour = "9pm",
    )

/**
 * The card is the one thing this app produces that lands in front of people who
 * have never seen it, and it is drawn on a phone that cannot be debugged. So it
 * is actually rendered here, pixels and all, rather than trusted to look right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsCardRendererTest {

    @Test
    fun `both shapes come out at the size the networks expect`() {
        val story = StatsCardRenderer.render(stats(), CardShape.Story)
        val square = StatsCardRenderer.render(stats(), CardShape.Square)

        assertEquals(1080, story.width)
        assertEquals(1920, story.height)
        assertEquals(1080, square.width)
        assertEquals(1080, square.height)
    }

    @Test
    fun `something is actually drawn, not just a black rectangle`() {
        val card = StatsCardRenderer.render(stats(), CardShape.Story)

        val distinct = buildSet {
            for (x in 0 until card.width step 17) {
                for (y in 0 until card.height step 17) add(card.getPixel(x, y))
            }
        }

        assertTrue("only ${distinct.size} colours, card looks empty", distinct.size > 5)
    }

    @Test
    fun `the accent appears, so the wordmark and bars rendered`() {
        val card = StatsCardRenderer.render(stats(), CardShape.Story)
        val acid = Color.rgb(0xDD, 0x2A, 0x7B)

        var found = false
        loop@ for (x in 0 until card.width step 3) {
            for (y in 0 until card.height step 3) {
                if (card.getPixel(x, y) == acid) {
                    found = true
                    break@loop
                }
            }
        }

        assertTrue("no accent pixel anywhere", found)
    }

    @Test
    fun `a day with nothing on it still produces a card`() {
        // Someone opening the app on a fresh morning. The renderer skips the
        // chart rather than dividing by a zero maximum.
        val card = StatsCardRenderer.render(
            stats(reels = 0, week = List(7) { 0 }),
            CardShape.Story,
        )

        assertEquals(1080, card.width)
    }

    @Test
    fun `a four figure count does not break the layout`() {
        val card = StatsCardRenderer.render(stats(reels = 4321), CardShape.Square)

        assertEquals(1080, card.width)
    }

    @Test
    fun `a software avatar bitmap is drawn without breaking the card`() {
        // The path the suite never exercised: a real avatar, which on a device
        // is what left the card stuck. A software bitmap must draw; the renderer
        // must return a whole card, not throw.
        val avatar = android.graphics.Bitmap
            .createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.rgb(0x51, 0x5B, 0xD4)) }

        val card = StatsCardRenderer.render(stats(), CardShape.Square, avatar)

        assertEquals(1080, card.width)
        assertEquals(1080, card.height)
    }

    @Test
    fun `the shorts look renders its own red peak and a split line`() {
        val red = Color.rgb(0xE0, 0x00, 0x1B)
        val card = StatsCardRenderer.render(
            stats().copy(label = "Reels + Shorts today", split = "120 Reels · 45 Shorts"),
            CardShape.Square,
            palette = CardPalette(
                canvas = Color.rgb(0xFF, 0xF8, 0xF7),
                chip = Color.rgb(0xFD, 0xEC, 0xEC),
                line = Color.rgb(0xF2, 0xE4, 0xE3),
                accent = red,
                accentDim = Color.rgb(0xF4, 0xA7, 0xA7),
                gradient = intArrayOf(
                    Color.rgb(0xA8, 0x00, 0x18), Color.rgb(0xD0, 0x00, 0x1A), red,
                    Color.rgb(0xFF, 0x3B, 0x30), Color.rgb(0xFF, 0x7A, 0x45),
                ),
            ),
        )
        assertEquals(1080, card.width)
        var found = false
        loop@ for (x in 0 until card.width step 3) {
            for (y in 0 until card.height step 3) {
                if (card.getPixel(x, y) == red) {
                    found = true
                    break@loop
                }
            }
        }
        assertTrue("no Shorts red anywhere", found)
    }

    @Test
    fun `an empty username is survivable`() {
        val card = StatsCardRenderer.render(
            stats().copy(username = "", peakHour = null),
            CardShape.Story,
        )

        assertEquals(1920, card.height)
    }
}
