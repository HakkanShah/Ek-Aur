package com.ekaur.android.copy

/**
 * What the counter says when it decides to say something.
 *
 * The app plays a deadpan hype-man: it cheers you on while the number it is
 * cheering quietly indicts you. Dry, short, a little unhinged -- the humour of a
 * good reply-guy, not a wellness app. Nothing scolds or says stop; the joke only
 * works played straight.
 *
 * Lines are grouped by how far gone you are, picked at random so a long session
 * does not repeat, and never hardcoded in a composable.
 */
object SarcasmCatalogue {

    /** Fires every this many reels. */
    const val MILESTONE_EVERY = 10

    private val early = listOf(
        "warming up.",
        "rookie numbers.",
        "we're just getting started.",
        "sure, one more.",
        "thumb's not even tired yet.",
        "this is nothing. keep going.",
        "barely a dent.",
        "no notes. continue.",
    )

    private val building = listOf(
        "fifty. respectable. worrying.",
        "the thumb has stamina, I'll give you that.",
        "halfway to a problem.",
        "no one can stop you. no one is trying.",
        "locked in. tragically.",
        "more? yeah, more.",
        "turning pro.",
        "this is commitment. wrong kind, but still.",
    )

    private val century = listOf(
        "triple digits. impressive. concerning.",
        "100. you could've learned a language.",
        "only 100? that was the warm-up.",
        "the thumb deserves a small trophy.",
        "consistency king. of this, specifically.",
        "still time to keep going. do it.",
        "100 down, infinity to go.",
        "certified. unclear for what.",
    )

    private val deep = listOf(
        "this isn't a hobby anymore, it's a career.",
        "scientists are confused.",
        "your thumb has filed for overtime.",
        "the algorithm loves you. it's the only one awake.",
        "don't tell your doctor.",
        "you're in the top 1%. of concern.",
        "just a bit more, then sleep. (lie.)",
        "impressive stamina. tragic use of it.",
    )

    private val legendary = listOf(
        "legend status. 💀",
        "send the thumb to the olympics.",
        "scrolling is the whole personality now.",
        "phone's hot. thumb's hot. touch grass.",
        "no records left to break.",
        "you're a machine. a sad, efficient machine.",
        "stop. no, don't. 😈",
        "they'll write about this. briefly.",
    )

    /** Overrides everything else -- the hour is funnier than the number. */
    private val lateNight = listOf(
        "still up? respect. concern, but respect.",
        "sleep is a myth apparently.",
        "the sun's coming for you.",
        "sleep? never met her.",
        "your eyes are filing a complaint.",
        "tomorrow-you is going to lose.",
    )

    /**
     * Lines for the milestones that fire at most once a day.
     *
     * Keyed by [com.ekaur.android.milestone.Milestone.copyKey], so a rule never
     * carries its own wording. Kept short: the pill gives a line two rows at a
     * small size, and an ellipsis mid-joke kills it.
     */
    private val byKey: Map<String, List<String>> = mapOf(
        "reels_50" to listOf(
            "50. warm-up done.",
            "fifty deep. thumb's fine, thanks.",
            "50 and counting. of course.",
        ),
        "reels_100" to listOf(
            "only 100? that was the warm-up.",
            "triple digits. impressive. concerning.",
            "100 reels. easy.",
        ),
        "reels_200" to listOf(
            "200. this stopped being a hobby.",
            "double century. a legend, technically.",
            "200. scientists are confused.",
        ),
        "reels_500" to listOf(
            "500. send the thumb to the olympics.",
            "500 reels. history, of a sort.",
            "500. no records left to break.",
        ),
        "session_30" to listOf(
            "30 minutes straight. locked in.",
            "half an hour, no breaks. keep going.",
        ),
        "session_60" to listOf(
            "one hour straight. respect.",
            "60 minutes, non-stop. machine.",
        ),
        "session_120" to listOf(
            "two hours. a record's breaking somewhere.",
            "2 hours straight. 💀 dedication.",
        ),
        "night_1am" to listOf(
            "1am and still here. the night is young.",
            "1am? respect.",
        ),
        "night_3am" to listOf(
            "3am. the algorithm's only friend left.",
            "welcome to the 3am club.",
        ),
    )

    /** Which milestone keys have copy written for them. */
    val copyKeys: Set<String> get() = byKey.keys

    private var lastLine: String? = null

    /**
     * A line for this milestone, avoiding whatever was said last time.
     *
     * [hour] is the local hour, 0-23; between 1am and 5am the time of night
     * replaces the count as the subject.
     */
    fun lineFor(count: Int, hour: Int): String {
        val pool = when {
            hour in 1..4 -> lateNight
            count >= 400 -> legendary
            count >= 200 -> deep
            count >= 100 -> century
            count >= 50 -> building
            else -> early
        }
        return pick(pool)
    }

    /**
     * A line for a once-a-day milestone.
     *
     * Falls back to the count-based line if a key has no copy, so a rule added
     * without its wording degrades to something sensible instead of silence.
     */
    fun lineForKey(key: String, count: Int, hour: Int): String =
        byKey[key]?.let(::pick) ?: lineFor(count, hour)

    /** Picks from [pool], avoiding whatever was said last time. */
    private fun pick(pool: List<String>): String {
        val choices = pool.filterNot { it == lastLine }.ifEmpty { pool }
        return choices.random().also { lastLine = it }
    }

    /**
     * The pill's resting face.
     *
     * Degrades as the number climbs, so the emoji carries the commentary when
     * there is no line on screen.
     */
    fun faceFor(count: Int): String = when {
        count >= 350 -> "💀"
        count >= 200 -> "🫠"
        count >= 100 -> "😵‍💫"
        count >= 50 -> "😮‍💨"
        count >= 25 -> "🙂"
        else -> "👀"
    }
}
