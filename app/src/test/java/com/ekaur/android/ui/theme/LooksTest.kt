package com.ekaur.android.ui.theme

import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.ui.common.AppWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LooksTest {

    private val insta = setOf(TrackedApp.Instagram)
    private val yt = setOf(TrackedApp.YouTube)
    private val both = setOf(TrackedApp.Instagram, TrackedApp.YouTube)

    @Test
    fun `the look follows the apps being counted`() {
        assertEquals(AppLook.Instagram, Looks.lookFor(insta))
        assertEquals(AppLook.Shorts, Looks.lookFor(yt))
        assertEquals(AppLook.Both, Looks.lookFor(both))
        assertEquals(AppLook.Instagram, Looks.lookFor(emptySet()))
    }

    @Test
    fun `a look picked by hand wins`() {
        assertEquals(AppLook.Shorts, Looks.lookFor(insta, override = AppLook.Shorts))
        assertEquals(AppLook.Instagram, Looks.lookFor(both, override = AppLook.Instagram))
    }

    @Test
    fun `every palette has five sweep stops and four button stops`() {
        for (look in AppLook.entries) {
            val p = Palette.of(look)
            assertEquals(look.name, 5, p.stops.size)
            assertEquals(look.name, 4, p.buttonStops.size)
        }
    }

    @Test
    fun `the cross-fade starts at one palette and ends at the other`() {
        val a = Palette.Instagram
        val b = Palette.Shorts
        val start = Palette.lerp(a, b, 0f)
        val end = Palette.lerp(a, b, 1f)
        assertEquals(a.canvas, start.canvas)
        assertEquals(a.stops, start.stops)
        assertEquals(b.accent, end.accent)
        assertEquals(b.buttonStops, end.buttonStops)
    }

    @Test
    fun `the number is named after the apps`() {
        assertEquals("Reels today", AppWords.today(insta))
        assertEquals("Shorts today", AppWords.today(yt))
        assertEquals("Reels + Shorts today", AppWords.today(both))
        assertEquals("Instagram or YouTube", AppWords.appNames(both))
        assertEquals("YouTube", AppWords.appNames(yt))
    }

    @Test
    fun `the split line appears only when both apps have a count`() {
        assertEquals(
            "120 Reels · 45 Shorts",
            AppWords.split(mapOf(TrackedApp.Instagram to 120, TrackedApp.YouTube to 45)),
        )
        assertEquals(
            "1 Reel · 1 Short",
            AppWords.split(mapOf(TrackedApp.Instagram to 1, TrackedApp.YouTube to 1)),
        )
        assertNull(AppWords.split(mapOf(TrackedApp.Instagram to 120, TrackedApp.YouTube to 0)))
        assertNull(AppWords.split(emptyMap()))
    }
}
