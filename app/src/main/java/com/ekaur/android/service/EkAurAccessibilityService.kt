package com.ekaur.android.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.ekaur.android.EkAurApp
import com.ekaur.android.detect.DetectionEvent
import com.ekaur.android.detect.DetectorRules
import com.ekaur.android.detect.ReelDetector
import com.ekaur.android.detect.ScrollSignal
import com.ekaur.android.diagnostics.CapturedEvent
import com.ekaur.android.diagnostics.EventLog
import com.ekaur.android.diagnostics.ServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as EkAurApp).container
        eventLog = container.eventLog
        status = container.serviceStatus

        detector.reset()
        status.onConnected()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { s ->
            tickJob = s.launch {
                // Drives time-based transitions no incoming event would trigger:
                // a scroll burst settling, and a session going cold.
                while (isActive) {
                    delay(TICK_INTERVAL_MS)
                    handle(detector.onTick(System.currentTimeMillis()))
                }
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
                handle(
                    detector.onSignal(
                        ScrollSignal(
                            packageName = packageName,
                            kind = kind,
                            timestampMs = now,
                        )
                    )
                )
                status.onEvent(packageName, now, detector.state.name)
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
            itemIndex = event.pagerIndex(),
        )

        val produced = detector.onSignal(signal)
        val counted = produced.count { it is DetectionEvent.ReelScrolled }

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
                state = detector.state.name,
            )
        )

        status.onEvent(packageName, now, detector.state.name)
        handle(produced)
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
        tickJob?.cancel()
        tickJob = null
        scope?.cancel()
        scope = null
        detector.reset()
        if (::status.isInitialized) status.onDisconnected()
    }

    private fun handle(events: List<DetectionEvent>) {
        if (events.isEmpty()) return
        if (!::eventLog.isInitialized) return
        val reels = events.count { it is DetectionEvent.ReelScrolled }
        if (reels > 0) eventLog.incrementCount(reels)
        // Sessions are persisted once Room lands; for now they only drive the
        // detector's own lifecycle.
    }

    private fun Int.toKind(): ScrollSignal.Kind? = when (this) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ScrollSignal.Kind.WindowStateChanged
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ScrollSignal.Kind.WindowContentChanged
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> ScrollSignal.Kind.ViewScrolled
        else -> null
    }

    /**
     * Best guess at the pager position this scroll landed on.
     *
     * A full-screen snapping pager reports one visible item, so `fromIndex` and
     * `toIndex` agree and either is the current video. When they disagree the
     * view is an ordinary list rather than the player, and the detector falls
     * back to its settle-window path instead of trusting a bogus index.
     *
     * Provisional: confirmed against a real device dump before it is relied on.
     */
    private fun AccessibilityEvent.pagerIndex(): Int {
        if (eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return ScrollSignal.NO_INDEX
        if (fromIndex < 0) return ScrollSignal.NO_INDEX
        return if (fromIndex == toIndex) fromIndex else ScrollSignal.NO_INDEX
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L
    }
}
