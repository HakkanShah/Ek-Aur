package com.ekaur.android.sync

import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.prefs.SettingsStore
import com.ekaur.android.data.remote.SupabaseClient
import com.ekaur.android.data.remote.SyncException

/** What one sync attempt did, for the diagnostics screen to report. */
sealed interface SyncResult {
    data object NotJoined : SyncResult
    data class Uploaded(val days: Int) : SyncResult
    data object NothingToDo : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * Pushes changed daily totals, and nothing else.
 *
 * The order matters more than it looks. Rows are read, sent, and only then
 * re-read and cleared -- and cleared only where the device still holds exactly
 * what was sent. Counting does not stop while a request is in flight, so
 * clearing on "the request succeeded" alone would drop whatever was scrolled
 * during it.
 */
class Syncer(
    private val db: EkAurDatabase,
    private val settings: SettingsStore,
    private val client: SupabaseClient,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun syncNow(): SyncResult {
        if (!settings.joined.value) return SyncResult.NotJoined
        if (settings.userId == null) return SyncResult.NotJoined

        val dirty = db.dailyCounts().dirtyRows(SyncPlan.MAX_BATCH)
        if (dirty.isEmpty()) return SyncResult.NothingToDo

        val payload = SyncPlan.toUpload(dirty)

        return try {
            client.uploadDays(payload)

            // Re-read rather than reusing `dirty`: the row on disk may have
            // moved on since it was sent.
            val current = db.dailyCounts().dirtyRows(SyncPlan.MAX_BATCH)
            val settled = SyncPlan.syncedRows(payload, current)
            val at = now()
            for (row in settled) {
                db.dailyCounts().markSynced(
                    date = row.date,
                    packageName = row.packageName,
                    reelCount = row.reelCount,
                    activeMs = row.activeMs,
                    atMs = at,
                )
            }
            SyncResult.Uploaded(settled.size)
        } catch (e: SyncException) {
            // Everything stays dirty and is retried; nothing is lost by failing.
            SyncResult.Failed(e.error.toString())
        }
    }
}
