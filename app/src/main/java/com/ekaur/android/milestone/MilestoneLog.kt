package com.ekaur.android.milestone

/**
 * What has already been said today.
 *
 * The engine is pure, so the once-a-day rule needs somewhere durable to live:
 * without it, every restart would replay the same milestone. Implemented by the
 * counting repository, which is the only thing that touches the database.
 */
interface MilestoneLog {

    /** Ids already fired on [date], a local `yyyy-MM-dd`. */
    suspend fun firedOn(date: String): Set<String>

    /** Records [milestoneId] as used up for [date]. */
    suspend fun markFired(date: String, milestoneId: String, atMs: Long)

    /** Nothing is ever recorded. Used before the database is reachable. */
    companion object None : MilestoneLog {
        override suspend fun firedOn(date: String): Set<String> = emptySet()
        override suspend fun markFired(date: String, milestoneId: String, atMs: Long) = Unit
    }
}
