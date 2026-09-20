package com.ekaur.android.sync

/**
 * What a username may look like, decided on the device.
 *
 * This is the first of three layers, and the one that does most of the work:
 * it runs on every keystroke, costs nothing, and means a malformed name never
 * becomes a network request. Only a name that passes here is worth asking the
 * server about, and only after the typing stops.
 *
 * The rules mirror the database's `username_shape` check exactly. If these two
 * ever disagree, the screen says a name is fine and the insert rejects it, with
 * nothing on the phone to explain why.
 */
object Username {

    const val MIN = 3
    const val MAX = 16

    /** Why a name cannot be used, in the order a typist would hit them. */
    enum class Problem {
        Empty,
        TooShort,
        TooLong,
        BadStart,
        BadCharacter,
        DoubledSeparator,
        TrailingSeparator,
    }

    /**
     * Lower-cased and trimmed.
     *
     * Usernames are lower-case by construction -- the database check rejects
     * capitals outright -- so the field folds case as the user types rather
     * than accepting something it will later refuse.
     */
    fun normalise(input: String?): String = input.orEmpty().trim().lowercase()

    /** The first thing wrong with [input], or null when it is usable. */
    fun problemWith(input: String?): Problem? {
        val name = normalise(input)
        return when {
            name.isEmpty() -> Problem.Empty
            name.length < MIN -> Problem.TooShort
            name.length > MAX -> Problem.TooLong
            !name.first().isValidStart() -> Problem.BadStart
            name.any { !it.isAllowed() } -> Problem.BadCharacter
            name.hasDoubledSeparator() -> Problem.DoubledSeparator
            name.last().isSeparator() -> Problem.TrailingSeparator
            else -> null
        }
    }

    fun isValid(input: String?): Boolean = problemWith(input) == null

    /** What to put under the field. Never scolds; just says what to change. */
    fun message(problem: Problem): String = when (problem) {
        Problem.Empty -> "naam to rakho"
        Problem.TooShort -> "kam se kam $MIN akshar"
        Problem.TooLong -> "$MAX se zyada nahi"
        Problem.BadStart -> "letter ya number se shuru karo"
        Problem.BadCharacter -> "sirf a-z, 0-9, . aur _"
        Problem.DoubledSeparator -> ". ya _ do baar nahi"
        Problem.TrailingSeparator -> ". ya _ pe khatam nahi"
    }

    private fun Char.isAllowed(): Boolean =
        this in 'a'..'z' || this in '0'..'9' || isSeparator()

    private fun Char.isValidStart(): Boolean = this in 'a'..'z' || this in '0'..'9'

    private fun Char.isSeparator(): Boolean = this == '.' || this == '_'

    private fun String.hasDoubledSeparator(): Boolean =
        zipWithNext().any { (a, b) -> a.isSeparator() && b.isSeparator() }
}
