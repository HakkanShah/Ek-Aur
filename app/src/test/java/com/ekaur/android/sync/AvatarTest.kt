package com.ekaur.android.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTest {

    @Test
    fun `a huge photo is never decoded at full size`() {
        // 4000x3000 is around 48MB decoded, which is enough to kill the app.
        val sample = Avatar.sampleSizeFor(4000, 3000)

        assertTrue("sample $sample too small", sample >= 8)
        assertTrue("shorter side must stay usable", 3000 / sample >= Avatar.SIZE)
    }

    @Test
    fun `sampling always leaves enough pixels to crop from`() {
        // Whatever comes in, the decoded image must still cover the target on
        // its shorter side, or the crop would have to enlarge and go soft.
        val sizes = listOf(257 to 257, 512 to 384, 1080 to 1920, 4032 to 3024, 6000 to 8000)

        for ((w, h) in sizes) {
            val sample = Avatar.sampleSizeFor(w, h)
            assertTrue("$w x $h -> $sample", minOf(w, h) / sample >= Avatar.SIZE)
        }
    }

    @Test
    fun `an image already small enough is decoded as it is`() {
        assertEquals(1, Avatar.sampleSizeFor(256, 256))
        assertEquals(1, Avatar.sampleSizeFor(300, 200))
    }

    @Test
    fun `nonsense dimensions do not produce a nonsense divisor`() {
        // A divisor of zero would be a crash on a screen with no way to report.
        assertEquals(1, Avatar.sampleSizeFor(0, 0))
        assertEquals(1, Avatar.sampleSizeFor(-4, 100))
    }

    @Test
    fun `the crop is a centred square`() {
        val wide = Avatar.centreCrop(1000, 400)
        assertEquals(400, wide.side)
        assertEquals(300, wide.x)
        assertEquals(0, wide.y)

        val tall = Avatar.centreCrop(400, 1000)
        assertEquals(400, tall.side)
        assertEquals(0, tall.x)
        assertEquals(300, tall.y)

        val square = Avatar.centreCrop(500, 500)
        assertEquals(Avatar.Crop(0, 0, 500), square)
    }

    @Test
    fun `a new picture gets a new url, or caches would never update`() {
        val first = Avatar.urlFor("https://x.supabase.co", "abc", 100)
        val second = Avatar.urlFor("https://x.supabase.co", "abc", 200)

        assertTrue(first!!.endsWith("/avatars/abc.webp?v=100"))
        assertTrue(second != first)
    }

    @Test
    fun `no picture means no url at all`() {
        assertNull(Avatar.urlFor("https://x.supabase.co", "abc", null))
        assertNull(Avatar.urlFor("https://x.supabase.co", "", 100))
    }

    @Test
    fun `a trailing slash on the base does not double up`() {
        val url = Avatar.urlFor("https://x.supabase.co/", "abc", 1)

        assertTrue(url!!.startsWith("https://x.supabase.co/storage/"))
    }

    @Test
    fun `the fallback initial copes with whatever the name starts with`() {
        assertEquals("H", Avatar.initialOf("hakkan"))
        assertEquals("9", Avatar.initialOf("99problems"))
        assertEquals("A", Avatar.initialOf("_a.b"))
        assertEquals("?", Avatar.initialOf(""))
        assertEquals("?", Avatar.initialOf("..."))
    }
}
