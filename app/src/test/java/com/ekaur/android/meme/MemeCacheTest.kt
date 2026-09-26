package com.ekaur.android.meme

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemeCacheTest {

    @get:Rule val tmp = TemporaryFolder()

    /** The start of a real WebP file: "RIFF", a size, "WEBP". */
    private val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8X".toByteArray(Charsets.ISO_8859_1)

    private var clock = 1_000L
    private var next = 0
    private var online = true
    private var garbage = false

    private fun cache() = MemeCache(tmp.root, fetchOne = { exclude ->
        if (!online) null else {
            var id: String
            do { id = "gif${next++}" } while (id in exclude)
            id to (if (garbage) "<html>Too Many Requests</html>".toByteArray() else webp)
        }
    }, now = { clock++ })

    @Test
    fun `nothing yet means nothing to show`() {
        assertNull(cache().pick())
    }

    @Test
    fun `refill keeps three, and offline it just stops`() = runTest {
        val c = cache()
        assertTrue("reports full", c.refill())
        assertEquals(MemeCache.SIZE, c.size)

        online = false
        tmp.root.listFiles()!!.first { it.name.endsWith(".webp") }.delete()
        assertTrue("reports not full, so the job retries", !c.refill())
        assertEquals(MemeCache.SIZE - 1, c.size)
    }

    @Test
    fun `an error page saved in place of a meme is never kept`() = runTest {
        garbage = true
        val c = cache()
        assertTrue(!c.refill())
        assertEquals(0, c.size)
        assertNull(c.pick())
    }

    @Test
    fun `only real animated images pass the check`() {
        assertTrue(MemeCache.isAnimatedImage(webp))
        assertTrue(MemeCache.isAnimatedImage("GIF89a\u0001\u0000\u0001\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertTrue(!MemeCache.isAnimatedImage("{\"message\":\"Invalid key\"}".toByteArray()))
        assertTrue(!MemeCache.isAnimatedImage(ByteArray(4)))
    }

    @Test
    fun `each popup gets the meme shown longest ago`() = runTest {
        val c = cache()
        c.refill()
        val seen = (1..3).map { c.pick()!!.name }
        assertEquals("three different memes before any repeats", 3, seen.toSet().size)
        assertEquals(seen.first(), c.pick()!!.name)
    }

    @Test
    fun `once all are worn, a fresh one replaces the most shown`() = runTest {
        val c = cache()
        c.refill()
        val before = tmp.root.list()!!.filter { it.endsWith(".webp") }.toSet()
        repeat(4) { c.pick() }
        c.refill()
        val after = tmp.root.list()!!.filter { it.endsWith(".webp") }.toSet()
        assertEquals(MemeCache.SIZE, after.size)
        assertNotEquals(before, after)
        assertTrue(after.any { it !in before })
    }
}
