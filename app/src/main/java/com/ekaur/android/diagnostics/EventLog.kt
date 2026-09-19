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
class EventLog(private val capacity: Int = 400) {

    private val _events = MutableStateFlow<List<CapturedEvent>>(emptyList())
    val events: StateFlow<List<CapturedEvent>> = _events.asStateFlow()

    private val _liveCount = MutableStateFlow(0)
    val liveCount: StateFlow<Int> = _liveCount.asStateFlow()

    private val _capturing = MutableStateFlow(true)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    fun record(event: CapturedEvent) {
        if (!_capturing.value) return
        _events.update { current ->
            // Newest first, so the inspector shows recent activity without scrolling.
            (listOf(event) + current).take(capacity)
        }
    }

    fun incrementCount(by: Int = 1) {
        _liveCount.update { it + by }
    }

    fun setCapturing(enabled: Boolean) {
        _capturing.value = enabled
    }

    fun clear() {
        _events.value = emptyList()
    }

    fun resetCount() {
        _liveCount.value = 0
    }

    /** Oldest-first plain text, suitable for pasting into a chat. */
    fun exportText(): String {
        val ordered = _events.value.asReversed()
        return buildString {
            append("EK AUR event dump\n")
            append("events=").append(ordered.size)
            append("  counted=").append(ordered.count { it.counted })
            append("  liveCount=").append(_liveCount.value).append("\n\n")
            ordered.forEach { append(it.toLine()) }
        }
    }
}
