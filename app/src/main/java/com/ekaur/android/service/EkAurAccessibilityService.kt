package com.ekaur.android.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.ekaur.android.EkAurApp
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.detect.DetectionEvent
import com.ekaur.android.detect.DetectorRules
import com.ekaur.android.detect.ReelDetector
import com.ekaur.android.detect.ScrollSignal
import com.ekaur.android.diagnostics.CapturedEvent
import com.ekaur.android.diagnostics.EventLog
import com.ekaur.android.diagnostics.ServiceStatus
import com.ekaur.android.overlay.MilestoneAnnouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Translates `AccessibilityEvent` into [ScrollSignal] and feeds the detector.
 *
 * This class is the only place Android event types are understood; everything
 * downstream is plain Kotlin. It deliberately keeps no counting logic of its
 * own, so the logic stays unit-testable.
 */
class EkAurAccessibilityService : AccessibilityService() {

    // Rules only for the apps the user counts: an app switched off reads as
    // untracked, so its events are ignored and leaving to it ends the sitting.
    private val detector = ReelDetector(
        rulesFor = { pkg -> trackedRules(pkg) },
        // Read live, so a rotation or split screen is picked up. Only used to
        // recognise the first Shorts page flip before its height is known.
        screenHeightPx = { resources.displayMetrics.heightPixels },
        // Every judged Shorts burst goes into the event log, so a dump shows
        // why each swipe did or didn't count.
        trace = { pkg, line ->
            if (::eventLog.isInitialized) eventLog.note(pkg, line)
            if (::status.isInitialized) {
                PAGE_SIZE.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { status.onPageSize(pkg, it) }
            }
        },
    )

    /** Judges what each usage-stats reading means before anything acts on it. */
    private val foregroundPolicy = ForegroundPolicy()
    private lateinit var eventLog: EventLog
    private lateinit var status: ServiceStatus
    private lateinit var counters: CounterRepository

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var overlay: OverlayController? = null
    private var announcements: MilestoneAnnouncer? = null
    private var reminders: com.ekaur.android.reminder.ReminderWatcher? = null
    private var reminderPopup: com.ekaur.android.overlay.ReminderPopup? = null

    /** Minutes watched today, for the reminder card. */
    private var todayActiveMs: kotlinx.coroutines.flow.StateFlow<Long>? = null
    private lateinit var settings: com.ekaur.android.data.prefs.SettingsStore

    /** When a counted app was last in the foreground, seeded at connect. */
    @Volatile
    private var lastTrackedForegroundMs = 0L

    private fun trackedRules(pkg: String) =
        DetectorRules.forPackage(pkg)?.takeIf { ::settings.isInitialized && settings.isCounting(pkg) }

    /**
     * Narrows the system's delivery to the apps being counted, so with Shorts
     * switched off YouTube's events never even reach this process. The static
     * config lists both apps; this is only ever a subset of it.
     */
    private fun applyScope(apps: Set<com.ekaur.android.detect.TrackedApp>) {
        val info = serviceInfo ?: return
        val packages = apps.map { it.packageName }
            .ifEmpty { listOf(com.ekaur.android.detect.TrackedApp.Instagram.packageName) }
        if (info.packageNames?.toSet() == packages.toSet()) return
        info.packageNames = packages.toTypedArray()
        runCatching { serviceInfo = info }
    }

    /** Throttles the usage-stats read, which need not run every 250ms tick. */
    @Volatile
    private var lastForegroundCheckMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as EkAurApp).container
        eventLog = container.eventLog
        status = container.serviceStatus
        counters = container.counterRepository
        settings = container.settings

        instance = this
        disabling = false
        detector.reset()
        status.onConnected()
        // Seed here so a service enabled but never taken into Instagram still
        // switches itself off after the grace rather than staying on for ever.
        lastTrackedForegroundMs = System.currentTimeMillis()
        toast("Ek Aur is on")

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s

        // Follow the per-app switches live, so turning Shorts on or off in
        // the app takes effect at once without restarting the service.
        s.launch {
            settings.countedApps.collect { apps -> main.post { applyScope(apps) } }
        }

        // Today's persisted total drives the pill, so it reads the same number
        // as the app rather than a separate session counter.
        val todayCount = counters.observeTodayCount()
            .stateIn(s, SharingStarted.Eagerly, 0)
        // Owned here rather than inside the composable, so an announcement is
        // not cut short when the window hides. The repository is its milestone
        // log, so a milestone stays fired across a service restart.
        val announcer = MilestoneAnnouncer(s, todayCount, log = counters)
        announcements = announcer
        overlay = OverlayController(this, todayCount, s, announcer)

        // The scroll reminder: a popup when today's count reaches the user's number.
        todayActiveMs = counters.observeTodayActiveMs().stateIn(s, SharingStarted.Eagerly, 0L)
        val popup = com.ekaur.android.overlay.ReminderPopup(this)
        reminderPopup = popup
        val dayClock = com.ekaur.android.data.repo.DayClock()
        reminders = com.ekaur.android.reminder.ReminderWatcher(
            scope = s,
            counts = todayCount,
            store = settings,
            today = { dayClock.dateOf(System.currentTimeMillis()) },
            onRemind = { count -> main.post { showReminder(count, dayClock) } },
        )

        tickJob = s.launch {
            // Drives time-based transitions no incoming event would trigger:
            // a scroll burst settling, and a session going cold.
            while (isActive) {
                // Four times a second while something is settling; once a
                // second when nothing is (outside Instagram and YouTube).
                delay(if (detector.isResting) RESTING_TICK_INTERVAL_MS else TICK_INTERVAL_MS)
                val nowMs = System.currentTimeMillis()
                val result = detector.onTick(nowMs)
                handle(result.events)
                overlay?.onDetectionState(result.state)
                checkForeground(nowMs)
            }
        }
    }

    /**
     * Reads the real foreground and acts on the user leaving Instagram.
     *
     * Two things follow from it, both settled in [AutoOffPolicy] rather than
     * here: the pill comes down soon after they leave, and the service switches
     * itself off a little later so a payment app is clean without a manual
     * pause. Neither touches counting -- a wrong read only affects what is on
     * screen, never a number. Throttled, since usage stats need not be read on
     * every 250ms tick.
     */
    private fun checkForeground(nowMs: Long) {
        if (nowMs - lastForegroundCheckMs < FOREGROUND_CHECK_INTERVAL_MS) return
        lastForegroundCheckMs = nowMs

        val ime = currentInputMethod()
        val foreground = ForegroundWatch.currentForegroundPackage(this, nowMs) { pkg ->
            foregroundPolicy.isTransient(pkg, ime)
        }
        status.onForegroundRead(foreground)
        // Unknown (can't tell, or a first sighting of somewhere else) changes
        // nothing: a wrong "left" would take the pill down, or switch the
        // service off, mid-scroll.
        val inTracked = when (
            foregroundPolicy.read(foreground, foreground != null && trackedRules(foreground) != null, ime)
        ) {
            ForegroundPolicy.Reading.InTracked -> true
            ForegroundPolicy.Reading.Left -> false
            ForegroundPolicy.Reading.Unknown -> return
        }
        if (inTracked) lastTrackedForegroundMs = nowMs

        overlay?.onForeground(inTracked)
        // Left Instagram/YouTube with the reminder up (Home, Recents): it has
        // done its job, and must not sit over some other app.
        if (!inTracked) main.post { reminderPopup?.dismiss() }

        val shouldDisable = AutoOffPolicy.shouldDisable(
            enabled = settings.autoOffOnLeave,
            usageGranted = ServiceControl.hasUsageAccess(this),
            foregroundIsTracked = inTracked,
            msSinceTrackedForeground = nowMs - lastTrackedForegroundMs,
            graceMs = AUTO_OFF_GRACE_MS,
        )
        if (shouldDisable && !disabling) {
            // Once only: the check runs every 1.5s, and a second switch-off
            // landing after the service has already gone would reach a dead
            // connection and take the app down with it.
            disabling = true
            settings.lastAutoOff = "$nowMs|$foreground"
            main.post {
                // onUnbind announces "off"; disableSelf tears the service down.
                runCatching { disableSelf() }
            }
        }
    }

    private fun showReminder(count: Int, dayClock: com.ekaur.android.data.repo.DayClock) {
        val popup = reminderPopup ?: return
        val reminder = settings.reminderSettings
        // The app the reminder fired in: the one "Take a break" closes.
        val scrollingIn = status.lastEventPackage.value?.takeIf { trackedRules(it) != null }
        popup.show(
            count = count,
            unit = com.ekaur.android.ui.common.AppWords.unit(settings.countedApps.value),
            minutesToday = ((todayActiveMs?.value ?: 0L) / 60_000L).toInt(),
            snooze = reminder.snooze,
            line = com.ekaur.android.copy.SarcasmCatalogue.reminderLine(),
        ) { choice ->
            val today = dayClock.dateOf(System.currentTimeMillis())
            when (choice) {
                // The next reminder is already N more away (set when shown).
                com.ekaur.android.overlay.ReminderPopup.Choice.Later -> Unit
                com.ekaur.android.overlay.ReminderPopup.Choice.Break -> takeABreak(scrollingIn)
                com.ekaur.android.overlay.ReminderPopup.Choice.NotToday -> {
                    val day = com.ekaur.android.reminder.ReminderPlan.dayFor(reminder, settings.reminderDay, today)
                    settings.reminderDay = com.ekaur.android.reminder.ReminderPlan.onNotToday(day)
                }
                com.ekaur.android.overlay.ReminderPopup.Choice.TurnOff -> {
                    settings.updateReminder(settings.reminderSettings.copy(enabled = false), today)
                    toast("Reminders off. Back on from Home any time.")
                }
            }
        }
    }

    /**
     * "Take a break": leave the app, then close it.
     *
     * No ordinary app may force-close another, accessibility or not. Going
     * Home pauses the video at once; ending the app's process once it is in
     * the background then closes it for real, so it starts fresh next time.
     * That second step works up to Android 13 -- from 14 Android only lets an
     * app end its own processes, and the break is just the Home screen.
     */
    private fun takeABreak(pkg: String?) {
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        val name = pkg?.let { com.ekaur.android.detect.TrackedApp.entries.firstOrNull { app -> app.packageName == it }?.appName }
        toast(if (name != null) "Closed $name. Go touch grass. 🌱" else "Break time. Go touch grass. 🌱")
        if (pkg == null) return
        val activity = getSystemService(android.app.ActivityManager::class.java) ?: return
        // Twice: the app has to have actually reached the background, which
        // on a slow phone can take a moment after Home.
        for (delayMs in longArrayOf(800L, 2_500L)) {
            main.postDelayed({ runCatching { activity.killBackgroundProcesses(pkg) } }, delayMs)
        }
    }

    /** Set once the service has asked to switch itself off. */
    @Volatile
    private var disabling = false

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** The keyboard's package: it floats over apps and is never a destination. */
    private fun currentInputMethod(): String? = runCatching {
        android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.substringBefore('/')
    }.getOrNull()

    private fun toast(text: String) {
        main.post {
            android.widget.Toast.makeText(applicationContext, text, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::eventLog.isInitialized) return

        val packageName = event.packageName?.toString() ?: return
        val kind = event.eventType.toKind() ?: return
        val now = System.currentTimeMillis()

        // The service is scoped to the counted apps in its config (and narrowed
        // at runtime), so in practice only their events ever arrive. This stays
        // as a guard: anything else -- including an app the user switched off --
        // is ignored outright, never inspected, never recorded.
        if (trackedRules(packageName) == null) return

        // The service declares no window-content capability, so there is no node
        // to read and no view id to be had -- counting works off the event's own
        // report of a one-item pager advancing. Left null rather than queried, so
        // the code says plainly that the screen is never read.
        val viewId: String? = null
        val scrollDeltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
        val scrollDeltaX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0

        val signal = ScrollSignal(
            packageName = packageName,
            kind = kind,
            timestampMs = now,
            className = event.className?.toString(),
            viewId = viewId,
            contentDescription = event.contentDescription?.toString(),
            scrollDeltaY = scrollDeltaY,
            scrollDeltaX = scrollDeltaX,
            fromIndex = event.indexOrNone(event.fromIndex),
            toIndex = event.indexOrNone(event.toIndex),
        )

        val result = detector.onSignal(signal)
        val counted = result.events.count { it is DetectionEvent.ReelScrolled }

        eventLog.record(
            CapturedEvent(
                timestampMs = now,
                packageName = packageName,
                eventType = kind.name,
                className = signal.className,
                viewId = viewId,
                contentDescription = signal.contentDescription,
                scrollDeltaY = scrollDeltaY,
                scrollDeltaX = scrollDeltaX,
                scrollY = event.scrollY,
                fromIndex = event.fromIndex,
                toIndex = event.toIndex,
                itemCount = event.itemCount,
                counted = counted > 0,
                state = result.state.name,
            )
        )

        status.onEvent(packageName, now, result.state.name)
        handle(result.events)
        overlay?.onDetectionState(result.state)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // Fires however the service was switched off -- floating button, tile,
        // settings, or its own auto-off -- so the user always sees it happen,
        // so the user always sees it happen.
        if (::eventLog.isInitialized) toast("Ek Aur is off")
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        overlay?.destroy()
        overlay = null
        announcements = null
        // Called on the main thread (onUnbind/onDestroy), as the window needs.
        reminderPopup?.dismiss()
        reminderPopup = null
        reminders = null
        todayActiveMs = null
        tickJob?.cancel()
        tickJob = null
        scope?.cancel()
        scope = null
        detector.reset()
        if (::status.isInitialized) status.onDisconnected()
    }

    /**
     * Persists what the detector concluded.
     *
     * Accessibility callbacks arrive on the main thread, so the database work is
     * handed to the service's own scope rather than blocking event delivery --
     * a slow write must never cost us a scroll event.
     */
    private fun handle(events: List<DetectionEvent>) {
        if (events.isEmpty()) return
        if (!::eventLog.isInitialized) return

        val reels = events.count { it is DetectionEvent.ReelScrolled }
        if (reels > 0) {
            eventLog.incrementCount(reels)
            // Said before the database write that moves the count, so the
            // announcer can tell a real reel from today's stored total
            // arriving after a restart.
            announcements?.onReelCounted()
            reminders?.onReelCounted()
        }

        // How long this sitting has run is only knowable here, and it has to be
        // set before the count that crosses a duration milestone is handled.
        for (event in events) {
            when (event) {
                is DetectionEvent.SessionStarted -> announcements?.onSessionStarted(event.timestampMs)
                is DetectionEvent.SessionEnded -> announcements?.onSessionEnded()
                is DetectionEvent.ReelScrolled -> Unit
            }
        }

        val scope = scope ?: return
        scope.launch {
            for (event in events) {
                runCatching {
                    when (event) {
                        is DetectionEvent.ReelScrolled -> counters.onReelScrolled(event)
                        is DetectionEvent.SessionEnded -> counters.onSessionEnded(event)
                        is DetectionEvent.SessionStarted -> Unit
                    }
                }.onFailure { eventLog.recordWriteFailure(it) }
            }
        }
    }

    private fun Int.toKind(): ScrollSignal.Kind? = when (this) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ScrollSignal.Kind.WindowStateChanged
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ScrollSignal.Kind.WindowContentChanged
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> ScrollSignal.Kind.ViewScrolled
        else -> null
    }

    /**
     * Positions are only meaningful on a scroll event; elsewhere they are stale
     * or unset. The detector decides what the pair of them means -- that
     * judgement stays in pure Kotlin where it can be tested.
     */
    private fun AccessibilityEvent.indexOrNone(value: Int): Int =
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && value >= 0) {
            value
        } else {
            ScrollSignal.NO_INDEX
        }

    companion object {
        private const val TICK_INTERVAL_MS = 250L

        /** The tick while the detector has nothing pending: fewer wake-ups, same behaviour. */
        private const val RESTING_TICK_INTERVAL_MS = 1_000L

        /** Picks the learned page height out of a detector trace line. */
        private val PAGE_SIZE = Regex("""size=(\d+)""")

        /** Usage stats need not be read every tick; ~1.5s is plenty. */
        private const val FOREGROUND_CHECK_INTERVAL_MS = 1_500L

        /**
         * How long the user must be out of Instagram before the service switches
         * itself off. Short enough that a payment right after Instagram is clean,
         * long enough that a quick reply or a glance at a notification survives.
         * Tuned on the device.
         */
        private const val AUTO_OFF_GRACE_MS = 8_000L

        // The one live service, so the UI can switch it off for a payment. Held
        // as a plain reference rather than passed around: nothing outside this
        // class may reach the service, and it is cleared the moment the service
        // goes, so a stale one can never be used.
        @Volatile
        private var instance: EkAurAccessibilityService? = null

        /**
         * Turns the service off from inside the app, for when a payment app is
         * about to be used.
         *
         * `disableSelf` removes the service from the system's enabled list at
         * once, which is what a UPI app's warning is reading -- so this is the
         * reliable way to quiet even the crudest check. Turning it back on has
         * to happen in Settings, because an app is never allowed to grant itself
         * accessibility; [ServiceControl.openAccessibilitySettings] is the way
         * back. Returns false if the service was not running to begin with.
         */
        fun pauseFromUi(): Boolean {
            val live = instance ?: return false
            return runCatching { live.disableSelf() }.isSuccess
        }
    }
}
