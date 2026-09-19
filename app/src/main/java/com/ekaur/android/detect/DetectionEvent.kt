package com.ekaur.android.detect

/** What the detector concluded. Consumed by the service and the counting pipeline. */
sealed interface DetectionEvent {

    /** One short-form video was scrolled past. The unit the whole app counts. */
    data class ReelScrolled(
        val packageName: String,
        val timestampMs: Long,
    ) : DetectionEvent

    data class SessionStarted(
        val packageName: String,
        val timestampMs: Long,
    ) : DetectionEvent

    data class SessionEnded(
        val packageName: String,
        val startedAtMs: Long,
        val endedAtMs: Long,
        val reelCount: Int,
    ) : DetectionEvent {
        val durationMs: Long get() = endedAtMs - startedAtMs
    }
}

/** Where the detector currently thinks the user is. */
enum class DetectionState {
    /** No app we care about is in the foreground. */
    Idle,

    /** A supported app is open, but not in its short-form player. */
    InApp,

    /** The short-form player is on screen. Only here does anything count. */
    InReels,
}
