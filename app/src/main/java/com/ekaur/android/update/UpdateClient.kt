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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
     * the file on success, null on any failure (a half-written file is deleted so
     * a failed download is never mistaken for a ready one).
     */
    fun download(url: String, dest: File, onProgress: (Float) -> Unit): File? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "EkAur-Updater")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStream = response.body?.byteStream() ?: return null
                val total = response.body?.contentLength() ?: -1L

                dest.parentFile?.mkdirs()
                var read = 0L
                bodyStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                onProgress(1f)
                dest
            }
        }.getOrElse {
            dest.delete()
            null
        }
    }
}
