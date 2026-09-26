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

    private var clock = 1_000L
    private var next = 0
    private var online = true

    private fun cache() = MemeCache(tmp.root, fetchOne = { exclude ->
        if (!online) null else {
            var id: String
            do { id = "gif${next++}" } while (id in exclude)
            id to byteArrayOf(1, 2, 3)
        }
    }, now = { clock++ })

    @Test
    fun `nothing yet means nothing to show`() {
        assertNull(cache().pick())
    }

    @Test
    fun `refill keeps three, and offline it just stops`() = runTest {
        val c = cache()
        c.refill()
        assertEquals(MemeCache.SIZE, c.size)

        online = false
        tmp.root.listFiles()!!.first { it.name.endsWith(".webp") }.delete()
        c.refill()
        assertEquals(MemeCache.SIZE - 1, c.size)
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
