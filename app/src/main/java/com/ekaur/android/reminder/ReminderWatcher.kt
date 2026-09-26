package com.ekaur.android.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watches today's count and asks for the popup when [ReminderPlan] says so.
 *
 * Built like the milestone announcer: the count flow starts with whatever is
 * stored for today, so nothing may fire until detection has counted a reel of
 * its own -- otherwise turning the service on at 150 would pop a reminder
 * about a number reached hours ago.
 */
class ReminderWatcher(
    scope: CoroutineScope,
    counts: StateFlow<Int>,
    private val store: ReminderStore,
    private val today: () -> String,
    private val onRemind: (count: Int) -> Unit,
) {

    @Volatile
    private var seenAReel = false

    init {
        scope.launch {
            var before: Int? = null
            counts.collect { count ->
                val previous = before
                before = count
                if (previous == null || !seenAReel) return@collect
                check(count, previous)
            }
        }
    }

    /** Told by the service the moment detection counts a reel. */
    fun onReelCounted() {
        seenAReel = true
    }

    private fun check(count: Int, before: Int) {
        val settings = store.reminderSettings
        if (!settings.enabled) return
        val day = ReminderPlan.dayFor(settings, store.reminderDay, today())
        if (!ReminderPlan.shouldRemind(settings, day, count, before)) {
            // Keep a new day's state, so Home reads it back correctly.
            if (store.reminderDay?.date != day.date) store.reminderDay = day
            return
        }
        // Scheduled before it is shown: see ReminderPlan.
        store.reminderDay = ReminderPlan.onShown(day, count, settings)
        onRemind(count)
    }
}
