package com.ekaur.android.update

import com.ekaur.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Talks to the GitHub releases API and downloads the APK asset.
 *
 * The check is one unauthenticated GET against the public repo -- no token ships
 * in the app, and asset downloads follow GitHub's redirect to storage on their
 * own. All parsing and the newer-than decision live in [UpdateResolver]; this is
 * only the wire.
 */
class UpdateClient(
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // A 14 MB APK over a weak connection needs a patient read window, or a
        // slow-but-alive transfer times out mid-stream and half-writes the file.
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val apiBase: String = "https://api.github.com",
) {

    /** The latest published release, or null if there is none or the call fails. */
    fun latest(): Release? {
        val request = Request.Builder()
            .url("$apiBase/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EkAur-Updater")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return null
                UpdateResolver.parseLatest(body)
            }
        }.getOrNull()
    }

    /**
     * Streams [url] into [dest], reporting progress as a 0..1 fraction. Returns
     * the file only when the download is provably complete and looks like an APK;
     * otherwise null, with nothing left at [dest].
     *
     * [expectedSize] is the asset's byte count from the API. It is the real guard:
     * a throttled or dropped transfer with no Content-Length would otherwise end
     * early, be taken as "done", and hand the installer a truncated file -- which
     * is exactly the "problem parsing the package" failure. We write to a temp
     * file, verify the byte count and the ZIP magic, and only then move it into
     * place, so a partial download is never offered as ready.
     */
    fun download(
        url: String,
        dest: File,
        expectedSize: Long? = null,
        onProgress: (Float) -> Unit,
    ): File? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "EkAur-Updater")
            .build()

        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")

        val ok = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                val bodyStream = response.body?.byteStream() ?: return@runCatching false
                val total = expectedSize
                    ?: response.body?.contentLength()?.takeIf { it > 0 }

                var read = 0L
                bodyStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total != null) {
                                onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }

                // The whole point: refuse anything short of the full asset, and
                // refuse a body that isn't a ZIP/APK (e.g. an HTML error page).
                val complete = expectedSize == null || read == expectedSize
                complete && looksLikeApk(part)
            }
        }.getOrDefault(false)

        if (!ok) {
            part.delete()
            dest.delete()
            return null
        }

        dest.delete()
        val moved = part.renameTo(dest) || run {
            runCatching { part.copyTo(dest, overwrite = true) }.isSuccess
        }
        part.delete()
        if (!moved || !dest.exists()) {
            dest.delete()
            return null
        }
        onProgress(1f)
        return dest
    }

    companion object {
        /** True when [file] begins with the ZIP local-file header `PK\u0003\u0004`. */
        fun looksLikeApk(file: File): Boolean = runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(4)
                if (input.read(head) < 4) return false
                head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                    head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
    }
}
