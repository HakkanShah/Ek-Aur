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
        // Joke numbers: each fires only on its own reel, so the pill reads the
        // same number the line is about.
        "exact_7" to listOf(
            "7. Thala for a reason.",
            "Seven. Thala for a reason. 🦁",
        ),
        "exact_18" to listOf(
            "18. Kohli's jersey. same hunger, different sport.",
            "Eighteen. that's a King Kohli number. 👑",
        ),
        "exact_49" to listOf(
            "49. RCB all out, 2017. you're still batting.",
            "49. RCB's lowest. your thumb has better form.",
        ),
        "exact_69" to listOf(
            "69. nice.",
            "69. nice. 😏",
            "Sixty-nine. we're not saying it. nice.",
        ),
        "exact_99" to listOf(
            "99. one away from a century. ek aur? 😏",
            "Nervous nineties. don't get out now.",
            "99. the next one is literally the app's name.",
        ),
        "exact_111" to listOf(
            "111. Nelson. hop on one leg till 112.",
            "111. the umpire's hopping. you're scrolling.",
        ),
        "exact_143" to listOf(
            "143. I love you. the algorithm says it back. 💘",
            "143. it's not love, it's the feed.",
        ),
        "exact_175" to listOf(
            "175. Kapil Dev in '83. you, on a weeknight.",
            "175*. Kapil saved a World Cup. you saved nothing.",
        ),
        "exact_183" to listOf(
            "183*. Dhoni's best. helicopter shot to the next reel. 🚁",
            "183. Thala's number. finish it like him.",
        ),
        "exact_264" to listOf(
            "264. Rohit's ODI record. you just matched it.",
            "264. Hitman numbers. wrong pitch.",
        ),
        "exact_404" to listOf(
            "404. self-control not found.",
            "404. sleep not found. try again later.",
        ),
        "exact_420" to listOf(
            "420. char sau bees. the feed's been scamming you.",
            "420. the algorithm is a fraud and you know it.",
        ),
        "exact_666" to listOf(
            "666. even the devil says take a break. 😈",
            "666. the feed has claimed your soul. 😈",
        ),
        "exact_973" to listOf(
            "973. Kohli's IPL 2016. he needed 16 games. you needed today.",
            "973. that's King Kohli's best season. in reels. 👑",
        ),
        "exact_2011" to listOf(
            "2011. Dhoni finishes off in style. you… don't. 🏆",
            "2011. World Cup year. world record thumb.",
        ),

        "reels_25" to listOf(
            "25 in. warmed up. 😎",
            "Twenty-five. thumb's stretching.",
            "25 and locked in already.",
            "25. warm-up done. the real match starts now.",
        ),
        "reels_50" to listOf(
            "50. warm-up done. 🌚",
            "Fifty deep. thumb's fine, thanks.",
            "50 and counting. of course.",
            "Half-century. raise the bat. 🏏",
            "50. the thumb just took guard.",
        ),
        "reels_100" to listOf(
            "Only 100? that was the warm-up.",
            "Triple digits. impressive. concerning.",
            "100 reels. easy. 😈",
            "Century. helmet off, bat up. 🏏",
            "100. Sachin would be proud. of the numbers.",
        ),
        "reels_150" to listOf(
            "150. no brakes on this thing. 😵‍💫",
            "One-fifty. the thumb has opinions now.",
            "150 deep and picking up speed.",
            "150. the thumb is in its prime.",
        ),
        "reels_200" to listOf(
            "200. this stopped being a hobby.",
            "Double century. a legend, technically.",
            "200. scientists are confused.",
            "200. Sachin needed 147 balls. you needed a thumb.",
        ),
        "reels_250" to listOf(
            "250. quarter of a thousand. 😈",
            "Two-fifty. the feed calls you boss.",
            "250 reels. no witnesses.",
            "250. the feed has your number now.",
        ),
        "reels_300" to listOf(
            "300. the feed fears you now.",
            "Three hundred. no notes. 💀",
            "300 deep. touch grass? never.",
            "300. THIS. IS. SPARTA. 🛡️",
        ),
        "reels_400" to listOf(
            "400. this is your Roman Empire. 💀",
            "Four hundred. the thumb ascends.",
            "400 reels. absolutely feral.",
            "400. Lara's record. in reels. 🏏",
        ),
        "reels_500" to listOf(
            "500. send the thumb to the olympics.",
            "500 reels. history, of a sort.",
            "500. no records left to break. ☠️",
            "500. half a K. still no sign of stopping.",
        ),
        "reels_600" to listOf(
            "600. the algorithm filed a complaint.",
            "Six hundred. unhinged. iconic. 💀",
            "600 reels, zero regrets. (some regrets.)",
        ),
        "reels_750" to listOf(
            "750. this is a lifestyle now.",
            "Seven-fifty. the algorithm bows.",
            "750 reels. genuinely unwell. 💀",
        ),
        "reels_800" to listOf(
            "800. the phone is scared of you. ☠️",
            "Eight hundred. certified menace.",
            "800 reels. we stopped counting for you.",
        ),
        "reels_1000" to listOf(
            "1000. a thousand. legendary. ☠️",
            "Four digits. touch grass immediately.",
            "1K reels. they'll study you.",
            "1000. ek hazaar. Ek Aur, clearly.",
        ),
        "reels_1250" to listOf(
            "1250. a full-time job now. 💀",
            "Twelve-fifty. the thumb unionised.",
            "1.25K reels. simply not okay.",
        ),
        "reels_1500" to listOf(
            "1500. seek help (later). ☠️",
            "1.5K reels. record-breaking, personal-worst.",
            "1500. the grass misses you.",
        ),
        "reels_2000" to listOf(
            "2000. a cautionary tale. ☠️",
            "2K reels. they write songs about this.",
            "2000. the thumb has transcended.",
        ),
        "session_30" to listOf(
            "30 minutes straight. locked in.",
            "Half an hour, no breaks. keep going.",
            "30 minutes. a whole episode of reels.",
        ),
        "session_60" to listOf(
            "One hour straight. respect.",
            "60 minutes, non-stop. machine. 😈",
            "One hour. could've been a movie. was reels.",
        ),
        "session_120" to listOf(
            "Two hours. a record's breaking somewhere.",
            "2 hours straight. 💀 dedication.",
            "Two hours. a full IPL innings, but reels.",
        ),
        "night_12am" to listOf(
            "Midnight. the feed's just getting good. 🌚",
            "12am. sleep is for the weak, clearly.",
            "Past midnight and thriving. sort of.",
            "12am. new day, same thumb.",
        ),
        "night_1am" to listOf(
            "1am and still here. the night is young.",
            "1am? respect. concern, but respect.",
            "1am. the feed is running out of reels. it isn't.",
        ),
        "night_3am" to listOf(
            "3am. the algorithm's only friend left. 💀",
            "Welcome to the 3am club.",
            "3am. even the chai is asleep.",
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
     * The reminder popup's line. The one place the app admits it might be time
     * to stop -- because the user asked it to -- so it stays dry, never a lecture.
     */
    private val reminder = listOf(
        "You asked me to tap you on the shoulder. Tap tap. 👋",
        "Reminder, as requested. The feed will survive without you.",
        "This is your sign. You literally set it.",
        "Past-you wanted a word. Past-you had a point.",
        "Checkpoint reached. Save your game, go outside.",
        "The algorithm won't tell you to stop. So I will. Once.",
        "Water, stretch, blink. Then decide.",
        "You set this. I'm just the messenger. Don't shoot. 🫡",
    )

    fun reminderLine(): String = pick(reminder)

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
