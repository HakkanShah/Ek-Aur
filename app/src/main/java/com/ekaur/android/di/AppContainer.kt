package com.ekaur.android.di

import android.content.Context
import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.data.repo.DayClock
import com.ekaur.android.diagnostics.CrashReporter
import com.ekaur.android.diagnostics.EventLog
import com.ekaur.android.diagnostics.ServiceStatus

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
}
