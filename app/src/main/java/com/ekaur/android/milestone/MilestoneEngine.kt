package com.ekaur.android.milestone

/**
 * Decides whether this reel is worth remarking on.
 *
 * Pure Kotlin, no Android and no storage: it is handed where the user was, where
 * they are now, and what has already been said today, and it answers with a
 * milestone or nothing. Persisting the answer is the caller's job.
 */
class MilestoneEngine(
    private val milestones: List<Milestone> = MilestoneRules.DEFAULT,
) {

    /**
     * What this step earns, if anything.
     *
     * A trigger is satisfied by where the user *is*, not by the step that got
     * them there, and [firedToday] alone stops a repeat. Testing the step
     * instead looks tidier but loses milestones: two reels can arrive in one
     * update, a restart mid-day skips whatever happened across it, and a
     * threshold that loses a tie-break is never crossed a second time.
     *
     * Only one line is announced at a time, because the pill shows one. The
     * others stay eligible and arrive on later reels -- except those made
     * meaningless by a bigger one of the same kind, which are marked spent
     * without ever being shown. Without that, a device whose milestone log is
     * empty at a count of 300 would work through 200, 100 and 50 on three
     * consecutive reels.
     */
    fun evaluate(now: Progress, before: Progress, firedToday: Set<String>): Outcome? {
        // Nothing to celebrate when nothing happened. This also keeps the
        // clock-based milestones from firing on an idle tick.
        if (now.reels <= before.reels) return null

        val satisfied = milestones.filter { it.id !in firedToday && satisfies(it.trigger, now, before) }
        if (satisfied.isEmpty()) return null

        val superseded = supersededIn(satisfied)
        // The largest of each kind is never superseded, so this cannot be empty.
        val announce = satisfied.firstOrNull { it !in superseded } ?: return null

        return Outcome(
            announce = announce,
            spent = (superseded.map { it.id } + announce.id).distinct(),
        )
    }

    private fun satisfies(trigger: Trigger, now: Progress, before: Progress): Boolean =
        when (trigger) {
            is Trigger.CountReached -> now.reels >= trigger.reels

            is Trigger.CountExactly -> now.reels == trigger.reels && before.reels < trigger.reels

            is Trigger.SessionMinutes -> now.sessionMinutes >= trigger.minutes

            is Trigger.ClockBetween ->
                now.minuteOfDay >= trigger.hour * 60 + trigger.minute &&
                    now.minuteOfDay < trigger.untilHour * 60
        }

    /**
     * The satisfied milestones a bigger one of the same kind has made stale.
     *
     * Reaching 200 reels says everything that reaching 100 would have. The
     * small hours are not cumulative -- their windows do not overlap -- so a
     * clock milestone never supersedes anything, and neither does an exact
     * number, which is a joke about that number rather than a rung.
     */
    private fun supersededIn(satisfied: List<Milestone>): List<Milestone> {
        val topCount = satisfied.mapNotNull { (it.trigger as? Trigger.CountReached)?.reels }.maxOrNull()
        val topMinutes = satisfied.mapNotNull { (it.trigger as? Trigger.SessionMinutes)?.minutes }.maxOrNull()

        return satisfied.filter { milestone ->
            when (val trigger = milestone.trigger) {
                is Trigger.CountReached -> topCount != null && trigger.reels < topCount
                is Trigger.SessionMinutes -> topMinutes != null && trigger.minutes < topMinutes
                // Only true on its own reel, so there is nothing to catch up on.
                is Trigger.CountExactly -> false
                is Trigger.ClockBetween -> false
            }
        }
    }
}

/**
 * The result of one evaluation.
 *
 * [spent] is everything now used up for the day -- the announced milestone plus
 * any the caller should quietly retire. All of it has to be recorded, or the
 * retired ones come back on the next reel.
 */
data class Outcome(
    val announce: Milestone,
    val spent: List<String>,
)

/**
 * Where the user is, as far as milestones are concerned.
 *
 * [reels] is today's running total rather than this sitting's, because "100
 * reels" means what a person would mean by it.
 */
data class Progress(
    val reels: Int,
    val sessionMinutes: Int,
    val minuteOfDay: Int,
)
