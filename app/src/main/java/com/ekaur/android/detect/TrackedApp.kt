package com.ekaur.android.detect

/**
 * The apps Ek Aur counts, and what each one calls its short videos.
 *
 * One place for the package names and the words, so the service, the data
 * layer and the screens all agree on which app is which.
 */
enum class TrackedApp(
    val packageName: String,
    val appName: String,
    /** What one of its short videos is called: "Reel", "Short". */
    val item: String,
    val items: String,
) {
    Instagram("com.instagram.android", "Instagram", "Reel", "Reels"),
    YouTube("com.google.android.youtube", "YouTube", "Short", "Shorts");

    companion object {
        fun forPackage(pkg: String?): TrackedApp? = entries.firstOrNull { it.packageName == pkg }
    }
}
