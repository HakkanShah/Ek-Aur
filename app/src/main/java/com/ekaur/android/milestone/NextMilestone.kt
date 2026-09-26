package com.ekaur.android.milestone

/**
 * Where today's count sits on the milestone ladder: the rung just passed and
 * the next one up. Drives the "next roast at 100 · 23 to go" chip on Home.
 */
data class NextRoast(
    /** The last count milestone at or below the count, or 0. */
    val previous: Int,
    /** The next count milestone above the count, or null past the top rung. */
    val next: Int?,
    val count: Int,
) {
    val toGo: Int get() = next?.let { (it - count).coerceAtLeast(0) } ?: 0

    /** 0..1 progress from [previous] to [next]; 1 past the top rung. */
    val fraction: Float
        get() {
            val top = next ?: return 1f
            val span = (top - previous).coerceAtLeast(1)
            return ((count - previous).toFloat() / span).coerceIn(0f, 1f)
        }
}

object NextMilestone {

    /** The count rungs from [rules] -- round numbers and joke numbers alike -- ascending and de-duplicated. */
    fun ladder(rules: List<Milestone> = MilestoneRules.DEFAULT): List<Int> =
        rules.mapNotNull {
            when (val t = it.trigger) {
                is Trigger.CountReached -> t.reels
                is Trigger.CountExactly -> t.reels
                else -> null
            }
        }
            .filter { it > 0 }
            .distinct()
            .sorted()

    fun forCount(count: Int, rules: List<Milestone> = MilestoneRules.DEFAULT): NextRoast {
        val rungs = ladder(rules)
        val safe = count.coerceAtLeast(0)
        return NextRoast(
            previous = rungs.lastOrNull { it <= safe } ?: 0,
            next = rungs.firstOrNull { it > safe },
            count = safe,
        )
    }
}
