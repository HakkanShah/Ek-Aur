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
import kotlin.random.Random

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
            lock.withLock { runCatching { cache.refill() }.getOrDefault(false) }
        }
        prepare()
        return full
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
            val url = Giphy.searchUrl(
                apiKey = BuildConfig.GIPHY_API_KEY,
                query = Giphy.QUERIES.random(),
                // A random page, so two phones (or two days) don't get the same few.
                offset = Random.nextInt(0, 40),
            )
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                res.body?.string()
            } ?: return@runCatching null
            val pick = Giphy.parse(body).filter { it.id !in exclude }.randomOrNull() ?: return@runCatching null
            val bytes = http.newCall(Request.Builder().url(pick.url).build()).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                val data = res.body?.bytes() ?: return@runCatching null
                if (data.size > Giphy.MAX_BYTES * 2) return@runCatching null
                data
            }
            pick.id to bytes
        }.getOrNull()
    }
}
