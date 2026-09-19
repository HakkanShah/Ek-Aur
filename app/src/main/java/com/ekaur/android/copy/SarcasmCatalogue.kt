package com.ekaur.android.copy

/**
 * What the counter says when it decides to say something.
 *
 * The app plays a hype-man that cheers the user on, while the number it is
 * cheering quietly indicts them. Nothing here scolds, warns or suggests
 * stopping -- the joke only works if it is played completely straight.
 *
 * Lines are grouped by how far gone the user is, picked at random so a long
 * session does not repeat itself, and never hardcoded in a composable.
 */
object SarcasmCatalogue {

    /** Fires every this many reels. */
    const val MILESTONE_EVERY = 10

    private val early = listOf(
        "warm-up ho gaya 🔥",
        "abhi to shuru kiya hai 😏",
        "itna hi? aur chalao 🚀",
        "thumb garam ho raha hai 💪",
        "bas ek aur, pakka 🤞",
        "shabash, aur scroll 👏",
        "ye to kuch bhi nahi 😌",
        "chalte raho boss 🛴",
    )

    private val building = listOf(
        "half century! 🏏",
        "thumb ka stamina dekho 💪",
        "50 paar, peeche mat dekho 🏃",
        "koi rok nahi sakta 🚦",
        "focus level: unmatched 🎯",
        "aur? haan aur 🔁",
        "professional ban raha hai 📈",
        "ye dedication hai 🫡",
    )

    private val century = listOf(
        "CENTURY 🏏 helmet utaro",
        "100 reels, record toot gaya 📊",
        "sirf 100? warm-up tha 😎",
        "thumb ko medal do 🥉",
        "consistency king 👑",
        "abhi time hai... aur karo ⏰",
        "100 down, infinity to go ♾️",
        "selection pakka hai 🏆",
    )

    private val deep = listOf(
        "ye ab hobby nahi, career hai 💼",
        "scientists confused hain 🔬",
        "thumb ne gym join kar li 🏋️",
        "algorithm tujhse pyaar karta hai 💘",
        "doctor ko mat batana 🤫",
        "olympics me entry pakki 🥈",
        "bas thoda aur, phir sona 🌙",
        "stamina waste ho raha hai 😤",
    )

    private val legendary = listOf(
        "legend status 💀",
        "thumb ko olympics bhej de 🥇",
        "ab scroll hi zindagi hai 🫠",
        "phone garam, thumb garam 🔥",
        "koi record nahi bacha 🏆",
        "tu machine hai 🤖",
        "ruk ja... nahi, mat ruk 😈",
        "history me naam likha jayega 📜",
    )

    /** Overrides everything else -- the hour is funnier than the number. */
    private val lateNight = listOf(
        "raat ke is waqt bhi? respect 🫡",
        "sona overrated hai 🌙",
        "subah hone wali hai ☀️",
        "neend? kaunsi neend 👁️",
        "aankhein jal rahi hongi 🔥",
        "kal ka plan cancel 😵",
    )

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
