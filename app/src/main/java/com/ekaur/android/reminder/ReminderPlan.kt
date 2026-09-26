package com.ekaur.android.reminder

/**
 * The scroll reminder: a popup when today's count reaches a number the user
 * picked, with a way to push it back N more, stop it for the day, or turn it
 * off. Pure Kotlin, so every rule below is tested on the JVM.
 *
 * The one decision everything else follows from: **the next reminder is
 * scheduled the moment a popup is shown**, as if the user had pressed
 * "N more". Every other way the popup can end -- Back, leaving the app,
 * the service restarting, the phone killing it, a crash -- then already has
 * the right answer, and none of them can make the popup come back on the
 * very next reel. The buttons only change that answer when they mean
 * something else ("Not today", "Turn off").
 */

/** What the user set. [at] and [snooze] are counts of Reels + Shorts. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val at: Int = DEFAULT_AT,
    val snooze: Int = DEFAULT_SNOOZE,
) {
    companion object {
        const val DEFAULT_AT = 100
        const val DEFAULT_SNOOZE = 20
        const val MIN_AT = 5
        const val MAX_AT = 5_000

        /**
         * The numbers the slider snaps to: fine steps where people actually
         * set limits, bigger ones above.
         */
        val STOPS = listOf(10, 15, 20, 25, 30, 40, 50, 60, 75, 100, 125, 150, 175, 200, 250, 300, 400, 500)

        /** How many more a "remind me later" gives. */
        val SNOOZES = listOf(10, 20, 30, 50)
    }
}

/**
 * Today's reminder state. [nextAt] is the count the next popup waits for, or
 * null when the user said "Not today". Belongs to [date]; a new day starts
 * again from the settings.
 */
data class ReminderDay(
    val date: String,
    val nextAt: Int?,
)

object ReminderPlan {

    /** Today's state: the stored one if it is today's, else a fresh day. */
    fun dayFor(settings: ReminderSettings, stored: ReminderDay?, today: String): ReminderDay =
        if (stored != null && stored.date == today) stored else ReminderDay(today, settings.at)

    /**
     * Whether this reel brings the popup. Only a reel that moved the count
     * can: a count that arrives from storage (the service starting mid-day)
     * never pops anything up by itself.
     */
    fun shouldRemind(settings: ReminderSettings, day: ReminderDay, count: Int, before: Int): Boolean {
        if (!settings.enabled) return false
        val next = day.nextAt ?: return false
        return count > before && count >= next
    }

    /** Shown: the next one is already N more away, whatever happens to this one. */
    fun onShown(day: ReminderDay, count: Int, settings: ReminderSettings): ReminderDay =
        day.copy(nextAt = count + settings.snooze.coerceAtLeast(1))

    /** "Not today": quiet until tomorrow. */
    fun onNotToday(day: ReminderDay): ReminderDay = day.copy(nextAt = null)

    /**
     * The settings changed (switched on, a new number): today starts again
     * from the new number. If the count is already past it, the popup comes
     * on the next reel -- the limit the user just set has been passed.
     */
    fun onSettingsChanged(settings: ReminderSettings, today: String): ReminderDay =
        ReminderDay(today, settings.at)

    /** What the card on Home says about today. */
    fun status(settings: ReminderSettings, day: ReminderDay, count: Int): Status = when {
        !settings.enabled -> Status.Off
        day.nextAt == null -> Status.DoneForToday
        count >= day.nextAt -> Status.OnNextReel(day.nextAt)
        else -> Status.Waiting(day.nextAt, day.nextAt - count, snoozed = day.nextAt != settings.at)
    }

    sealed interface Status {
        data object Off : Status
        data object DoneForToday : Status

        /** Already past the number: the popup comes with the next reel. */
        data class OnNextReel(val at: Int) : Status

        data class Waiting(val at: Int, val toGo: Int, val snoozed: Boolean) : Status
    }

    /** A picked number, kept to something sensible. */
    fun clampAt(value: Int): Int = value.coerceIn(ReminderSettings.MIN_AT, ReminderSettings.MAX_AT)
}

/** Where the reminder's settings and today's state are kept. */
interface ReminderStore {
    val reminderSettings: ReminderSettings
    var reminderDay: ReminderDay?
}
