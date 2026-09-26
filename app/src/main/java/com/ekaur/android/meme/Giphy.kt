package com.ekaur.android.meme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reading GIPHY's answers, and choosing what to download.
 *
 * Pure Kotlin, tested on the JVM. GIPHY returns each GIF in a dozen renditions;
 * the popup shows it about 190dp tall, so the 200px-tall one is plenty (memes are
 * grainy by nature) and its animated WebP is a fraction of the GIF's size.
 */
object Giphy {

    /** One downloadable meme. */
    data class Candidate(val id: String, val url: String, val bytes: Long)

    /** Nothing larger is fetched: a reminder should never cost a megabyte. */
    const val MAX_BYTES = 450_000L

    private val json = Json { ignoreUnknownKeys = true }

    /** One GIF by its id; answers in the same shape as a search, so [parse] reads it. */
    fun byIdUrl(apiKey: String, id: String): String =
        "https://api.giphy.com/v1/gifs?api_key=${enc(apiKey)}&ids=${enc(id)}"

    /**
     * The website's list of GIF ids ({"ids": [...]}), keeping only well-formed
     * ids. Empty if the body isn't that shape.
     */
    fun parseIds(body: String): List<String> = runCatching {
        json.parseToJsonElement(body).jsonObject.getValue("ids").jsonArray
            .mapNotNull { it.jsonPrimitive.content.takeIf(CuratedMemes::isValidId) }
            .distinct()
    }.getOrDefault(emptyList())

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
