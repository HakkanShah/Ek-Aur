package com.ekaur.android.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a GitHub release tells us, reduced to the four things the app needs. */
data class Release(
    val versionName: String,
    /** Parsed from the APK asset's filename; null when it can't be read. */
    val versionCode: Int?,
    /** Direct download URL of the .apk asset, or null if the release has none. */
    val apkUrl: String?,
    val notes: String?,
    /** The release's own page, used as a fallback when a direct download can't. */
    val pageUrl: String,
    /**
     * The APK asset's exact byte size, from the API. The download is verified
     * against this, so a stream that ends early (a dropped or throttled
     * connection with no Content-Length) can never be offered as a valid APK.
     */
    val apkSize: Long? = null,
)

/**
 * Turns a GitHub `releases/latest` payload into a [Release] and decides whether it
 * is newer than the running build.
 *
 * Pure Kotlin, no Android, so the version logic -- the part that decides whether
 * to nag ten friends to update -- is unit-tested on the JVM rather than trusted
 * to look right on a phone.
 */
object UpdateResolver {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Payload(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0L,
    )

    /** `ekaur-v0.18.1-build37.apk` -> 37. The build number is the real version. */
    private val BUILD_IN_NAME = Regex("""build(\d+)""", RegexOption.IGNORE_CASE)

    fun buildFromAssetName(name: String): Int? =
        BUILD_IN_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Parses the payload of `GET /releases/latest`. Returns null for a body that
     * is not a release (a 404 "Not Found" when no release exists yet, or junk),
     * so the caller treats "no release" as "up to date" rather than crashing.
     */
    fun parseLatest(body: String): Release? {
        val payload = runCatching { json.decodeFromString<Payload>(body) }.getOrNull() ?: return null
        val tag = payload.tagName ?: return null

        val apk = payload.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        val versionName = tag.removePrefix("v").removePrefix("V")

        return Release(
            versionName = versionName,
            versionCode = apk?.name?.let(::buildFromAssetName),
            apkUrl = apk?.url?.takeIf { it.isNotBlank() },
            notes = payload.body?.trim()?.takeIf { it.isNotEmpty() },
            pageUrl = payload.htmlUrl ?: "https://github.com/HakkanShah/Ek-Aur/releases",
            apkSize = apk?.size?.takeIf { it > 0 },
        )
    }

    /**
     * Whether [release] is newer than the running build.
     *
     * By build number when the release carries one -- the only truly monotonic
     * signal -- and otherwise by comparing the tag as a semver, so a release
     * without a recognisable asset name still updates sensibly.
     */
    fun isNewer(currentCode: Int, currentName: String, release: Release): Boolean {
        val code = release.versionCode
        if (code != null) return code > currentCode
        return compareSemver(release.versionName, currentName) > 0
    }

    /** Compares dotted numeric versions; missing parts count as zero. */
    fun compareSemver(a: String, b: String): Int {
        val pa = a.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
