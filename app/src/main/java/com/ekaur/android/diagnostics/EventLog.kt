package com.ekaur.android.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory record of what the service is seeing, and the live count.
 *
 * There is no ADB on the test device, so this is how the app reports on itself:
 * the inspector screen renders this, and the share export ships it back as text.
 * Nothing here is persisted -- durable counting arrives with Room in the next
 * phase.
 */
class EventLog(private val capacity: Int = 2_000) {

    /**
     * A fixed ring, newest last. Recording is O(1): it used to rebuild a
     * 2,000-item list on every accessibility event, on the main thread --
     * cheap for Instagram, but YouTube fires far more scroll and window events,
     * and that copy ran constantly while scrolling. Now a list is only built
     * when the inspector (or the export) actually asks for one.
     */
    private val ring = ArrayDeque<CapturedEvent>(capacity)

    private val _revision = MutableStateFlow(0L)

    /** Bumps whenever the log changes; the inspector re-reads [snapshot] on it. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** The captured events, newest first. */
    fun snapshot(): List<CapturedEvent> = synchronized(ring) { ring.asReversed().toList() }

    private val _liveCount = MutableStateFlow(0)
    val liveCount: StateFlow<Int> = _liveCount.asStateFlow()

    private val _capturing = MutableStateFlow(true)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    /**
     * Last database write failure, if any.
     *
     * Surfaced on the diagnostics screen because with no logcat a failing write
     * is indistinguishable from detection having stopped: the number simply
     * stops moving.
     */
    private val _lastWriteError = MutableStateFlow<String?>(null)
    val lastWriteError: StateFlow<String?> = _lastWriteError.asStateFlow()

    private val _writeFailures = MutableStateFlow(0)
    val writeFailures: StateFlow<Int> = _writeFailures.asStateFlow()

    fun recordWriteFailure(error: Throwable) {
        _lastWriteError.value = "${error::class.simpleName}: ${error.message}"
        _writeFailures.value += 1
    }

    fun record(event: CapturedEvent) {
        if (!_capturing.value) return
        synchronized(ring) {
            if (ring.size >= capacity) ring.removeFirst()
            ring.addLast(event)
        }
        _revision.update { it + 1 }
    }

    fun incrementCount(by: Int = 1) {
        _liveCount.update { it + by }
    }

    fun setCapturing(enabled: Boolean) {
        _capturing.value = enabled
    }

    fun clear() {
        synchronized(ring) { ring.clear() }
        _revision.update { it + 1 }
    }

    fun resetCount() {
        _liveCount.value = 0
    }

    /** Oldest-first plain text, suitable for pasting into a chat. */
    fun exportText(): String {
        val ordered = synchronized(ring) { ring.toList() }
        return buildString {
            append("EK AUR event dump\n")
            append("events=").append(ordered.size)
            append("  counted=").append(ordered.count { it.counted })
            append("  liveCount=").append(_liveCount.value).append("\n\n")
            ordered.forEach { append(it.toLine()) }
        }
    }
}
