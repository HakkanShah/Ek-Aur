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

    private val early = listOf(
        "Warming up.",
        "Rookie numbers.",
        "We're just getting started.",
        "Sure, one more.",
        "Thumb's not even tired yet.",
        "This is nothing. keep going.",
        "Barely a dent.",
        "No notes. continue.",
    )

    private val building = listOf(
        "Fifty. respectable. worrying.",
        "The thumb has stamina, I'll give you that.",
        "Halfway to a problem.",
        "No one can stop you. no one is trying.",
        "Locked in. tragically.",
        "More? yeah, more.",
        "Turning pro.",
        "This is commitment. wrong kind, but still.",
    )

    private val century = listOf(
        "Triple digits. impressive. concerning.",
        "100. you could've learned a language.",
        "Only 100? that was the warm-up.",
        "The thumb deserves a small trophy.",
        "Consistency king. of this, specifically.",
        "Still time to keep going. do it.",
        "100 down, infinity to go.",
        "Certified. unclear for what.",
    )

    private val deep = listOf(
        "This isn't a hobby anymore, it's a career.",
        "Scientists are confused.",
        "Your thumb has filed for overtime.",
        "The algorithm loves you. it's the only one awake.",
        "Don't tell your doctor.",
        "You're in the top 1%. of concern.",
        "Just a bit more, then sleep. (lie.)",
        "Impressive stamina. tragic use of it.",
    )

    private val legendary = listOf(
        "Legend status. 💀",
        "Send the thumb to the olympics.",
        "Scrolling is the whole personality now.",
        "Phone's hot. thumb's hot. touch grass.",
        "No records left to break.",
        "You're a machine. a sad, efficient machine.",
        "Stop. no, don't. 😈",
        "They'll write about this. briefly.",
    )

    /** Overrides everything else -- the hour is funnier than the number. */
    private val lateNight = listOf(
        "Still up? respect. concern, but respect.",
        "Sleep is a myth apparently.",
        "The sun's coming for you.",
        "Sleep? never met her.",
        "Your eyes are filing a complaint.",
        "Tomorrow-you is going to lose.",
    )

    /**
     * Lines for the milestones that fire at most once a day.
     *
     * Keyed by [com.ekaur.android.milestone.Milestone.copyKey], so a rule never
     * carries its own wording. Kept short: the pill gives a line two rows at a
     * small size, and an ellipsis mid-joke kills it.
     */
    private val byKey: Map<String, List<String>> = mapOf(
        "reels_25" to listOf(
            "25 in. warmed up. 😎",
            "Twenty-five. thumb's stretching.",
            "25 and locked in already.",
        ),
        "reels_50" to listOf(
            "50. warm-up done. 🌚",
            "Fifty deep. thumb's fine, thanks.",
            "50 and counting. of course.",
        ),
        "reels_100" to listOf(
            "Only 100? that was the warm-up.",
            "Triple digits. impressive. concerning.",
            "100 reels. easy. 😈",
        ),
        "reels_200" to listOf(
            "200. this stopped being a hobby.",
            "Double century. a legend, technically.",
            "200. scientists are confused.",
        ),
        "reels_300" to listOf(
            "300. the feed fears you now.",
            "Three hundred. no notes. 💀",
            "300 deep. touch grass? never.",
        ),
        "reels_500" to listOf(
            "500. send the thumb to the olympics.",
            "500 reels. history, of a sort.",
            "500. no records left to break. ☠️",
        ),
        "reels_750" to listOf(
            "750. this is a lifestyle now.",
            "Seven-fifty. the algorithm bows.",
            "750 reels. genuinely unwell. 💀",
        ),
        "reels_1000" to listOf(
            "1000. a thousand. legendary. ☠️",
            "Four digits. touch grass immediately.",
            "1K reels. they'll study you.",
        ),
        "session_30" to listOf(
            "30 minutes straight. locked in.",
            "Half an hour, no breaks. keep going.",
        ),
        "session_60" to listOf(
            "One hour straight. respect.",
            "60 minutes, non-stop. machine. 😈",
        ),
        "session_120" to listOf(
            "Two hours. a record's breaking somewhere.",
            "2 hours straight. 💀 dedication.",
        ),
        "night_12am" to listOf(
            "Midnight. the feed's just getting good. 🌚",
            "12am. sleep is for the weak, clearly.",
            "Past midnight and thriving. sort of.",
        ),
        "night_1am" to listOf(
            "1am and still here. the night is young.",
            "1am? respect. concern, but respect.",
        ),
        "night_3am" to listOf(
            "3am. the algorithm's only friend left. 💀",
            "Welcome to the 3am club.",
        ),
    )

    /** Which milestone keys have copy written for them. */
    val copyKeys: Set<String> get() = byKey.keys

    /** The last few lines shown, so nothing repeats until the pool is exhausted. */
    private val recent = ArrayDeque<String>()
    private const val RECENT_MEMORY = 4

    /**
     * A line for this milestone, avoiding whatever was said last time.
     *
     * [hour] is the local hour, 0-23; from midnight to 5am the time of night
     * replaces the count as the subject.
     */
    fun lineFor(count: Int, hour: Int): String {
        val pool = when {
            hour in 0..4 -> lateNight
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

    /**
     * Picks from [pool], avoiding the last few lines shown across every pool, so
     * back-to-back milestones never echo each other or themselves.
     */
    private fun pick(pool: List<String>): String {
        val choices = pool.filterNot { it in recent }.ifEmpty { pool }
        return choices.random().also { chosen ->
            recent.addLast(chosen)
            while (recent.size > RECENT_MEMORY) recent.removeFirst()
        }
    }

    /**
     * The pill's resting face.
     *
     * Degrades as the number climbs, so the emoji carries the commentary when
     * there is no line on screen.
     */
    fun faceFor(count: Int): String = when {
        count >= 500 -> "☠️"
        count >= 300 -> "💀"
        count >= 150 -> "😈"
        count >= 75 -> "😵‍💫"
        count >= 25 -> "🌚"
        else -> "😎"
    }
}
