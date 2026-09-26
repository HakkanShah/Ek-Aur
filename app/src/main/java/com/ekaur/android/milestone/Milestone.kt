package com.ekaur.android.milestone

/**
 * What has to happen for a milestone to fire.
 *
 * Kept as data rather than code so adding one is an entry in
 * [MilestoneRules.DEFAULT], not a new branch somewhere.
 */
sealed interface Trigger {

    /** Today's running total reaches [reels]. */
    data class CountReached(val reels: Int) : Trigger

    /**
     * Today's total lands on exactly [reels] with this very reel.
     *
     * For the numbers that are only funny on the dot -- 69, 99, 973. Unlike
     * [CountReached] it is never caught up later: starting the app at 76 must
     * not announce "69" a reel afterwards, when the pill no longer says 69.
     */
    data class CountExactly(val reels: Int) : Trigger

    /** One unbroken sitting reaches [minutes]. */
    data class SessionMinutes(val minutes: Int) : Trigger

    /**
     * Still scrolling in the small hours, between [hour]:[minute] and
     * [untilHour].
     *
     * The window has an end on purpose. "Past 1am" without one is also true at
     * eleven at night, which is neither late nor funny.
     */
    data class ClockBetween(
        val hour: Int,
        val minute: Int = 0,
        val untilHour: Int = 5,
    ) : Trigger
}

/**
 * One thing worth remarking on, at most once a day.
 *
 * [copyKey] names a set of lines in
 * [com.ekaur.android.copy.SarcasmCatalogue] rather than carrying the text, so
 * the wording is never hardcoded next to the rule.
 */
data class Milestone(
    val id: String,
    val trigger: Trigger,
    val copyKey: String,
)
