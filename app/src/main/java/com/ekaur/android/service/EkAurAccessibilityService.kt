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

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as EkAurApp).container
        eventLog = container.eventLog
        status = container.serviceStatus
        counters = container.counterRepository

        detector.reset()
        status.onConnected()

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s

        // Today's persisted total drives the pill, so it reads the same number
        // as the app rather than a separate session counter.
        val todayCount = counters.observeTodayCount()
            .stateIn(s, SharingStarted.Eagerly, 0)
        // Owned here rather than inside the composable, so an announcement is
        // not cut short when the window hides.
        val announcer = MilestoneAnnouncer(s, todayCount)
        overlay = OverlayController(this, todayCount, s, announcer)

        tickJob = s.launch {
            // Drives time-based transitions no incoming event would trigger:
            // a scroll burst settling, and a session going cold.
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                val result = detector.onTick(System.currentTimeMillis())
                handle(result.events)
                overlay?.onDetectionState(result.state)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::eventLog.isInitialized) return

        val packageName = event.packageName?.toString() ?: return
        val kind = event.eventType.toKind() ?: return
        val now = System.currentTimeMillis()

        val tracked = DetectorRules.forPackage(packageName) != null

        // Events from untracked apps are not inspected or recorded -- only the
        // fact that the foreground moved away is used, so the detector can close
        // out the session. Nothing about other apps is read or kept.
        if (!tracked) {
            if (kind == ScrollSignal.Kind.WindowStateChanged) {
                val result = detector.onSignal(
                    ScrollSignal(
                        packageName = packageName,
                        kind = kind,
                        timestampMs = now,
                    )
                )
                handle(result.events)
                status.onEvent(packageName, now, result.state.name)
                overlay?.onDetectionState(result.state)
            }
            return
        }

        val source = runCatching { event.source }.getOrNull()
        val viewId = runCatching { source?.viewIdResourceName }.getOrNull()
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
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        overlay?.destroy()
        overlay = null
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
        if (reels > 0) eventLog.incrementCount(reels)

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

    private companion object {
        const val TICK_INTERVAL_MS = 250L
    }
}
