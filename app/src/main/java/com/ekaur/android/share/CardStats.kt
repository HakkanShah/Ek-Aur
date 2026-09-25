package com.ekaur.android.share

/** What a share card says. Plain data, so the drawing has nothing to decide. */
data class CardStats(
    val username: String,
    val reelsToday: Int,
    val activeMsToday: Long,
    /** Seven days, oldest first, zero-filled. */
    val week: List<Int>,
    val bestEver: Int,
    val peakHour: String?,
    /**
     * What the number is called, when it isn't plain Reels: "Shorts today",
     * "Reels + Shorts today". Null keeps the classic "Reels today".
     */
    val label: String? = null,
    /** "120 Reels · 45 Shorts" when both apps were used today; null otherwise. */
    val split: String? = null,
)

/**
 * The card's colours, as plain ints, so the renderer stays free of Compose.
 * Defaults are the Instagram look; the share screen passes the live one.
 */
data class CardPalette(
    val canvas: Int = 0xFFFBF7FB.toInt(),
    val chip: Int = 0xFFF3EEFB.toInt(),
    val line: Int = 0xFFECE7F2.toInt(),
    /** The dare line and the peak bar. */
    val accent: Int = 0xFFDD2A7B.toInt(),
    val accentDim: Int = 0xFFE7A6CC.toInt(),
    val gradient: IntArray = intArrayOf(
        0xFF515BD4.toInt(), 0xFF8134AF.toInt(), 0xFFDD2A7B.toInt(),
        0xFFF58529.toInt(), 0xFFFEDA77.toInt(),
    ),
) {
    override fun equals(other: Any?): Boolean =
        other is CardPalette && canvas == other.canvas && chip == other.chip && line == other.line &&
            accent == other.accent && accentDim == other.accentDim && gradient.contentEquals(other.gradient)

    override fun hashCode(): Int =
        listOf(canvas, chip, line, accent, accentDim, gradient.contentHashCode()).hashCode()
}

/** The two shapes a card is ever asked for. */
enum class CardShape(val width: Int, val height: Int, val label: String) {
    /** WhatsApp status, Instagram and Facebook stories. */
    Story(1080, 1920, "story"),

    /** Instagram and Facebook posts, X timelines, WhatsApp chat. */
    Square(1080, 1080, "post"),
}

/**
 * The line under the number, and the only thing on the card doing any selling.
 *
 * It is a dare rather than a boast: the number is already absurd, so the card
 * works by inviting someone to prove they are worse. Nothing here explains what
 * the app is -- the screenshot does that.
 */
object CardCopy {

    /**
     * Where the card sends people: the app's website, which explains it and
     * always serves the newest APK. [LINK] is the short form drawn on the card;
     * [URL] is the full, clickable one for the share caption.
     */
    const val LINK = "ek-aur.vercel.app"
    const val URL = "https://ek-aur.vercel.app/"

    fun challengeFor(reels: Int): String = when {
        reels >= 500 -> "You can't beat this. Don't try."
        reels >= 200 -> "Beat this. You won't."
        reels >= 100 -> "What's your number?"
        reels >= 50 -> "Bet you can't keep up."
        else -> "Today was just a warm-up."
    }

    /** The one honest label. Never a boast, never a scold. */
    fun subtitleFor(reels: Int): String = if (reels == 1) "Reel today" else "Reels today"
}
