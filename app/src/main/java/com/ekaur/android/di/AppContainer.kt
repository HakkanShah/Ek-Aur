package com.ekaur.android.di

import android.content.Context
import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.data.prefs.SettingsStore
import com.ekaur.android.data.remote.SupabaseClient
import com.ekaur.android.data.repo.DayClock
import com.ekaur.android.diagnostics.CrashReporter
import com.ekaur.android.diagnostics.EventLog
import com.ekaur.android.diagnostics.ServiceStatus
import com.ekaur.android.sync.DeviceKey
import com.ekaur.android.sync.Syncer
import com.ekaur.android.update.UpdateManager

/**
 * Manual dependency container, built once in [com.ekaur.android.EkAurApp].
 *
 * Deliberately not Hilt: this app wires an AccessibilityService and an overlay
 * that lives outside any Activity, which is exactly where Hilt's scoping gets
 * awkward, and Room already costs one KSP pass without adding a second.
 */
class AppContainer(appContext: Context) {

    /** Shared between the service (writer) and the inspector UI (reader). */
    val eventLog: EventLog = EventLog()

    val serviceStatus: ServiceStatus = ServiceStatus()

    val crashReporter: CrashReporter = CrashReporter(appContext)

    val clock: DayClock = DayClock()

    // Opened lazily so the database file is not touched until something counts.
    val database: EkAurDatabase by lazy { EkAurDatabase.build(appContext) }

    val counterRepository: CounterRepository by lazy { CounterRepository(database, clock) }

    /** This device's identity on the leaderboard. */
    val settings: SettingsStore = SettingsStore(appContext)

    /**
     * Stable across uninstalls, which is what lets a reinstall find its old
     * account without the user doing anything.
     */
    val deviceKey: String? = DeviceKey.of(
        android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        )
    )

    // Built lazily like the database: an app that never joins the leaderboard
    // never constructs an HTTP client or touches the network.
    val supabase: SupabaseClient by lazy { SupabaseClient(settings) }

    val syncer: Syncer by lazy { Syncer(database, settings, supabase) }

    /** Checks the app's GitHub releases and drives the in-app updater. */
    val updateManager: UpdateManager by lazy { UpdateManager(appContext) }
}
