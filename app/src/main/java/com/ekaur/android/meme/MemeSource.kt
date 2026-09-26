package com.ekaur.android.meme

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.ekaur.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Memes for the reminder popup, fetched from GIPHY ahead of time.
 *
 * Nothing is fetched when the popup opens: the popup takes whatever is already
 * on the phone, and a replacement is fetched in the background afterwards. So
 * the popup is instant, works offline, and a slow network can never keep it
 * blank. Only runs while the scroll reminder is on. With no API key built in,
 * nothing is fetched at all and the popup uses its emoji fallback.
 */
class MemeSource(context: Context) {

    private val http = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = MemeCache(File(context.applicationContext.filesDir, "memes"), ::fetchOne)
    private val resources = context.applicationContext.resources

    val hasKey: Boolean get() = BuildConfig.GIPHY_API_KEY.isNotBlank()

    /** One download at a time, whether the service or a background job asks. */
    private val lock = Mutex()

    /**
     * The next meme, already decoded, waiting in memory. An animated WebP
     * decodes to a small object (the compressed bytes and one frame buffer),
     * so holding one costs little, and the popup can go up the instant the
     * reminder fires -- no disk, no decode, no network in that moment.
     */
    @Volatile
    private var prepared: Drawable? = null

    /**
     * Tops the stash up. Safe to call often; does nothing without a key. True
     * once full, so a background job knows whether to try again later.
     */
    suspend fun refill(): Boolean {
        if (!hasKey) return true
        val full = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    allowed = currentList()
                    // Anything taken off the list (or saved by an older build's
                    // search) goes, so a vetoed GIF never shows again.
                    cache.prune(allowed.toSet())
                    cache.refill()
                }.getOrDefault(false)
            }
        }
        prepare()
        return full
    }

    /** The ids a meme may be fetched from: the curated list. */
    @Volatile
    private var allowed: List<String> = CuratedMemes.BUILT_IN

    private val listFile = File(context.applicationContext.filesDir, "memes-list.txt")

    /**
     * The curated list: from the website at most once a day, then kept on the
     * phone; the built-in copy if neither has one. A list that looks broken
     * (too short, or no valid ids) is ignored.
     */
    private fun currentList(): List<String> {
        val saved = runCatching { listFile.readText() }.getOrNull()
        val savedAt = saved?.substringBefore('|')?.toLongOrNull() ?: 0L
        val savedIds = saved?.substringAfter('|', "")?.split(',')?.filter(CuratedMemes::isValidId).orEmpty()
        val fresh = System.currentTimeMillis() - savedAt < LIST_MAX_AGE_MS
        if (fresh && savedIds.size >= MIN_LIST) return savedIds

        val remote = runCatching {
            http.newCall(Request.Builder().url(CuratedMemes.REMOTE_URL).build()).execute().use { res ->
                if (res.isSuccessful) Giphy.parseIds(res.body?.string().orEmpty()) else emptyList()
            }
        }.getOrDefault(emptyList())
        if (remote.size >= MIN_LIST) {
            runCatching { listFile.writeText("${System.currentTimeMillis()}|${remote.joinToString(",")}") }
            return remote
        }
        return savedIds.takeIf { it.size >= MIN_LIST } ?: CuratedMemes.BUILT_IN
    }

    /** Decodes the next meme into memory, if one isn't waiting already. Off the main thread. */
    fun prepare() {
        if (prepared != null) return
        prepared = next()
    }

    /**
     * The meme for the popup: the prepared one if waiting, else straight off
     * disk; null for the emoji fallback. Each drawable is used once.
     */
    fun take(): Drawable? {
        val ready = prepared
        prepared = null
        return ready ?: next()
    }

    private fun next(): Drawable? {
        val file = runCatching { cache.pick() }.getOrNull() ?: return null
        return decode(file)
    }

    private fun decode(file: File): Drawable? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // An AnimatedImageDrawable for animated WebP; played by the popup.
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
        } else {
            // Android 8: the first frame, still.
            BitmapDrawable(resources, BitmapFactory.decodeFile(file.path) ?: return null)
        }
    }.getOrNull()

    private suspend fun fetchOne(exclude: Set<String>): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext null
        runCatching {
            // A random GIF from the curated list, not one already on the phone.
            val id = (allowed - exclude).randomOrNull() ?: return@runCatching null
            val body = http.newCall(Request.Builder().url(Giphy.byIdUrl(BuildConfig.GIPHY_API_KEY, id)).build())
                .execute().use { res ->
                    if (!res.isSuccessful) return@runCatching null
                    res.body?.string()
                } ?: return@runCatching null
            val pick = Giphy.parse(body).firstOrNull() ?: return@runCatching null
            val bytes = http.newCall(Request.Builder().url(pick.url).build()).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                val data = res.body?.bytes() ?: return@runCatching null
                if (data.size > Giphy.MAX_BYTES * 2) return@runCatching null
                data
            }
            pick.id to bytes
        }.getOrNull()
    }

    private companion object {
        const val LIST_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        const val MIN_LIST = 5
    }
}
