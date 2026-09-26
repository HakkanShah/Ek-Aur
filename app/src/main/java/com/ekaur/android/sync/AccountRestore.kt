package com.ekaur.android.sync

import com.ekaur.android.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puts a recovered account back together on this phone: its picture, its
 * leaderboard visibility and its daily history.
 *
 * Reported after a reinstall: the app found the account and restored the
 * username, but the photo was gone and the stats started from zero. The
 * server still had all of it -- only the name was ever read back.
 *
 * Runs right after a recovery, and once per account on phones that
 * recovered before this existed. Best effort: offline, it tries again on the
 * next launch; it never lowers anything the phone already counted.
 */
object AccountRestore {

    /** Returns true once the account's profile and history are on this phone. */
    suspend fun run(container: AppContainer): Boolean = withContext(Dispatchers.IO) {
        val userId = container.settings.userId ?: return@withContext false
        runCatching {
            val profile = container.supabase.myProfile() ?: return@runCatching false
            container.settings.saveAvatar(profile.avatarVersion, profile.avatarKey)
            container.settings.setHidden(profile.hidden)
            container.counterRepository.restoreDays(container.supabase.myDays(), System.currentTimeMillis())
            container.settings.historyRestoredFor = userId
            true
        }.getOrDefault(false)
    }

    /** Whether this phone still owes the account a restore. */
    fun due(container: AppContainer): Boolean {
        val userId = container.settings.userId ?: return false
        return container.settings.historyRestoredFor != userId
    }
}
