package com.ekaur.android.meme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reading GIPHY's search results, and choosing what to download.
 *
 * Pure Kotlin, tested on the JVM. GIPHY returns each GIF in a dozen renditions;
 * the popup shows it about 190dp tall, so the 200px-tall one is plenty (memes are
 * grainy by nature) and its animated WebP is a fraction of the GIF's size.
 */
object Giphy {

    /** What the popup searches for. One is picked at random each time. */
    val QUERIES = listOf(
        "touch grass",
        "stop scrolling",
        "take a break",
        "go outside",
        "go to sleep",
        "put the phone down",
        "put your phone down",
        "log off",
    )

    /** One downloadable meme. */
    data class Candidate(val id: String, val url: String, val bytes: Long)

    /** Nothing larger is fetched: a reminder should never cost a megabyte. */
    const val MAX_BYTES = 450_000L

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The search endpoint. `rating=g` keeps it family-safe; stickers and clips
     * are left out, so it's GIFs only.
     */
    fun searchUrl(apiKey: String, query: String, offset: Int, limit: Int = 25): String =
        "https://api.giphy.com/v1/gifs/search?api_key=${enc(apiKey)}&q=${enc(query)}" +
            "&limit=$limit&offset=$offset&rating=g&lang=en&bundle=messaging_non_clips"

    /** The small renditions of each result, in the order GIPHY ranked them. */
    fun parse(body: String): List<Candidate> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val data = runCatching { root.getValue("data").jsonArray }.getOrNull() ?: return emptyList()
        return data.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val images = obj["images"] as? JsonObject ?: return@mapNotNull null
            pick(images)?.let { (url, bytes) -> Candidate(id, url, bytes) }
        }
    }

    /**
     * The first rendition that is animated WebP and small enough, trying the
     * 200px-tall one, then its lower-frame-rate twin, then the 100px one.
     */
    private fun pick(images: JsonObject): Pair<String, Long>? {
        for (name in listOf("fixed_height", "fixed_height_downsampled", "fixed_height_small")) {
            val r = images[name] as? JsonObject ?: continue
            val url = r["webp"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") } ?: continue
            val bytes = r["webp_size"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
            if (bytes in 1..MAX_BYTES) return url to bytes
        }
        return null
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
