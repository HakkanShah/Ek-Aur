package com.ekaur.android.meme

import java.io.File

/**
 * A few memes kept on the phone, so the reminder popup opens instantly and
 * works offline. Plain Kotlin over a folder, with the network behind
 * [fetchOne] -- so the rotation is tested on the JVM.
 *
 * - [pick] hands out the one shown longest ago (never shown counts as oldest),
 *   and marks it shown.
 * - [refill] tops the folder up to [SIZE]; once everything has been shown at
 *   least once it brings in one fresh meme and drops the most worn one, so the
 *   popup doesn't cycle the same three for ever.
 */
class MemeCache(
    private val dir: File,
    /** Downloads one new meme: its id and bytes, or null (offline, no key). */
    private val fetchOne: suspend (exclude: Set<String>) -> Pair<String, ByteArray>?,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private fun files(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }?.toList().orEmpty()

    val size: Int get() = files().size

    /** The meme to show now, or null if none has been downloaded yet. */
    @Synchronized
    fun pick(): File? {
        val file = files().minByOrNull { shownAt(it) } ?: return null
        markShown(file)
        return file
    }

    /**
     * Downloads until there are [SIZE] memes, plus one fresh if all are worn.
     * True once the stash is full -- false tells a background job to retry.
     */
    suspend fun refill(): Boolean {
        dir.mkdirs()
        var tries = 0
        while (size < SIZE && tries < SIZE + 2) {
            tries++
            if (!add()) return false
        }
        val all = files()
        if (all.size >= SIZE && all.all { shownAt(it) > 0L }) {
            val worn = all.maxByOrNull { timesShown(it) } ?: return size >= SIZE
            if (add()) {
                worn.delete()
                shownFile(worn).delete()
            }
        }
        return size >= SIZE
    }

    /** Drops saved memes whose id is no longer allowed (taken off the list). */
    @Synchronized
    fun prune(allowed: Set<String>) {
        for (f in files()) {
            if (f.name.removeSuffix(EXT) !in allowed) {
                f.delete()
                shownFile(f).delete()
            }
        }
    }

    private suspend fun add(): Boolean {
        val have = files().map { it.name.removeSuffix(EXT) }.toSet()
        val (id, bytes) = fetchOne(have) ?: return false
        // Only a real animated image is kept: a cut-off download or an error
        // page saved as a meme would show as an empty panel.
        if (!isAnimatedImage(bytes)) return false
        val safe = id.filter { it.isLetterOrDigit() }.ifEmpty { return false }
        val tmp = File(dir, "$safe.tmp")
        tmp.writeBytes(bytes)
        return tmp.renameTo(File(dir, "$safe$EXT"))
    }

    // Shown times are kept in a tiny sidecar file: "<last shown ms> <times>".
    private fun shownFile(f: File) = File(dir, f.name + ".seen")
    private fun seen(f: File): List<Long> =
        runCatching { shownFile(f).readText().split(' ').map { it.toLong() } }.getOrDefault(listOf(0L, 0L))
    private fun shownAt(f: File) = seen(f).getOrElse(0) { 0L }
    private fun timesShown(f: File) = seen(f).getOrElse(1) { 0L }
    private fun markShown(f: File) {
        runCatching { shownFile(f).writeText("${now()} ${timesShown(f) + 1}") }
    }

    companion object {
        const val SIZE = 3
        const val EXT = ".webp"

        /** "RIFF....WEBP" or "GIF8": the two formats GIPHY serves. */
        fun isAnimatedImage(bytes: ByteArray): Boolean {
            if (bytes.size < 12) return false
            val riff = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
            val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()
            return riff || gif
        }
    }
}
