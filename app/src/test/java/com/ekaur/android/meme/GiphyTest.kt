package com.ekaur.android.meme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GiphyTest {

    private fun rendition(url: String, size: Long) = """{"webp":"$url","webp_size":"$size","url":"x.gif","size":"999999"}"""

    private val body = """
        {"data":[
          {"id":"abc123","images":{
             "fixed_height":${rendition("https://media.giphy.com/abc/200.webp", 180000)},
             "fixed_height_small":${rendition("https://media.giphy.com/abc/100.webp", 60000)}}},
          {"id":"huge","images":{
             "fixed_height":${rendition("https://media.giphy.com/huge/200.webp", 2400000)},
             "fixed_height_downsampled":${rendition("https://media.giphy.com/huge/200d.webp", 420000)}}},
          {"id":"toobig","images":{
             "fixed_height":${rendition("https://media.giphy.com/t/200.webp", 3000000)}}},
          {"id":"insecure","images":{
             "fixed_height":${rendition("http://media.giphy.com/i/200.webp", 1000)}}},
          {"images":{}}
        ],"meta":{"status":200}}
    """.trimIndent()

    @Test
    fun `takes the 200px WebP when it is small enough`() {
        val first = Giphy.parse(body).first()
        assertEquals("abc123", first.id)
        assertEquals("https://media.giphy.com/abc/200.webp", first.url)
    }

    @Test
    fun `falls back to a lighter rendition, and skips what is still too big`() {
        val ids = Giphy.parse(body).associate { it.id to it.url }
        assertEquals("https://media.giphy.com/huge/200d.webp", ids["huge"])
        assertTrue("toobig" !in ids)
    }

    @Test
    fun `never downloads over plain http, and ignores malformed items`() {
        val ids = Giphy.parse(body).map { it.id }
        assertEquals(listOf("abc123", "huge"), ids)
    }

    @Test
    fun `garbage in, nothing out`() {
        assertEquals(emptyList<Giphy.Candidate>(), Giphy.parse("not json"))
        assertEquals(emptyList<Giphy.Candidate>(), Giphy.parse("""{"message":"Invalid authentication credentials"}"""))
    }

    @Test
    fun `the search is family-safe and encodes the query`() {
        val url = Giphy.searchUrl("KEY", "touch grass", offset = 7)
        assertTrue(url.contains("q=touch+grass"))
        assertTrue(url.contains("rating=g"))
        assertTrue(url.contains("offset=7"))
    }
}
