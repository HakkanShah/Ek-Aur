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
)

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

    /** Where the card sends people. One constant, changed when a site exists. */
    const val LINK = "github.com/HakkanShah/Ek-Aur"

    fun challengeFor(reels: Int): String = when {
        reels >= 500 -> "you can't beat this. don't try."
        reels >= 200 -> "beat this. you won't."
        reels >= 100 -> "what's your number?"
        reels >= 50 -> "bet you can't keep up."
        else -> "today was just a warm-up."
    }

    /** The one honest label. Never a boast, never a scold. */
    fun subtitleFor(reels: Int): String = if (reels == 1) "reel today" else "reels today"
}
