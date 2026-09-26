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
    fun `a GIF is fetched by its id`() {
        val url = Giphy.byIdUrl("KEY", "abc123XYZ")
        assertTrue(url.startsWith("https://api.giphy.com/v1/gifs?"))
        assertTrue(url.contains("ids=abc123XYZ"))
    }

    @Test
    fun `the website list keeps only well-formed ids`() {
        val ids = Giphy.parseIds("""{"ids":["q5jnZ0d18LEtOgAICr","bad id!","q5jnZ0d18LEtOgAICr","x","T7xwGxSMc1oUfvSRNh"]}""")
        assertEquals(listOf("q5jnZ0d18LEtOgAICr", "T7xwGxSMc1oUfvSRNh"), ids)
        assertEquals(emptyList<String>(), Giphy.parseIds("<html>502</html>"))
    }

    @Test
    fun `the built-in list is sane`() {
        val ids = CuratedMemes.BUILT_IN
        assertTrue("a real choice of memes", ids.size >= 20)
        assertEquals("no duplicates", ids.size, ids.toSet().size)
        assertTrue("every id well-formed", ids.all(CuratedMemes::isValidId))
    }

}
