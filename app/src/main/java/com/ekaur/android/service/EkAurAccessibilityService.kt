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

    private val detector = ReelDetector()
    private lateinit var eventLog: EventLog
    private lateinit var status: ServiceStatus
    private lateinit var counters: CounterRepository

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var overlay: OverlayController? = null
    private var announcements: MilestoneAnnouncer? = null
    private lateinit var settings: com.ekaur.android.data.prefs.SettingsStore

    /** When Instagram was last the foreground app, seeded at connect. */
    @Volatile
    private var lastInstagramForegroundMs = 0L

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
        detector.reset()
        status.onConnected()
        // Seed here so a service enabled but never taken into Instagram still
        // switches itself off after the grace rather than staying on for ever.
        lastInstagramForegroundMs = System.currentTimeMillis()
        toast("Ek Aur chalu")

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s

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

        tickJob = s.launch {
            // Drives time-based transitions no incoming event would trigger:
            // a scroll burst settling, and a session going cold.
            while (isActive) {
                delay(TICK_INTERVAL_MS)
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

        val foreground = ForegroundWatch.currentForegroundPackage(this, nowMs)
        // Null means "cannot tell" -- treated as unchanged, never as "left",
        // because a wrong "left" would switch the service off mid-scroll.
        val inInstagram = when (foreground) {
            null -> return
            else -> DetectorRules.forPackage(foreground) != null
        }
        if (inInstagram) lastInstagramForegroundMs = nowMs

        overlay?.onForeground(inInstagram)

        val shouldDisable = AutoOffPolicy.shouldDisable(
            enabled = settings.autoOffOnLeave,
            usageGranted = ServiceControl.hasUsageAccess(this),
            foregroundIsInstagram = inInstagram,
            msSinceInstagramForeground = nowMs - lastInstagramForegroundMs,
            graceMs = AUTO_OFF_GRACE_MS,
        )
        if (shouldDisable) {
            main.post {
                // onUnbind announces "band"; disableSelf tears the service down.
                disableSelf()
            }
        }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

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

        // The service is scoped to Instagram in its config, so in practice only
        // Instagram events ever arrive. This stays as a guard: anything else is
        // ignored outright, never inspected, never recorded. A sitting is closed
        // by the detector's own idle timer instead of by watching other apps --
        // the price of not being able to see them, which is the whole point.
        if (DetectorRules.forPackage(packageName) == null) return

        // The service declares no window-content capability, so there is no node
        // to read and no view id to be had -- counting works off the event's own
        // report of a one-item pager advancing. Left null rather than queried, so
        // the code says plainly that the screen is never read.
        val viewId: String? = null
        val scrollDeltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0

        val signal = ScrollSignal(
            packageName = packageName,
            kind = kind,
            timestampMs = now,
            className = event.className?.toString(),
            viewId = viewId,
            contentDescription = event.contentDescription?.toString(),
            scrollDeltaY = scrollDeltaY,
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
        // settings, or its own auto-off -- so the user always sees it happen.
        if (::eventLog.isInitialized) toast("Ek Aur band")
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
            live.disableSelf()
            return true
        }
    }
}
