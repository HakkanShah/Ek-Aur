package com.ekaur.android.overlay

import com.ekaur.android.copy.SarcasmCatalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Decides when the pill has something to say, and for how long.
 *
 * Lives outside the composable on purpose. Held in `remember`, the message and
 * the milestone bookkeeping died with the overlay window every time it hid --
 * which cut announcements off part-way and let the same milestone fire twice.
 * Owned by the service, it survives the window coming and going.
 */
class MilestoneAnnouncer(
    scope: CoroutineScope,
    counts: StateFlow<Int>,
    private val clockHour: () -> Int = { LocalTime.now().hour },
) {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var announcement: Job? = null

    init {
        scope.launch {
            var previous: Int? = null
            counts.collect { count ->
                val before = previous
                previous = count

                // The first value is whatever was already stored for today, not
                // something that just happened, so it announces nothing.
                if (before == null || count <= before) return@collect

                // Fire when the count crosses a multiple, rather than landing
                // exactly on one -- two reels can arrive in a single update.
                val every = SarcasmCatalogue.MILESTONE_EVERY
                if (count / every > before / every) announce(count, scope)
            }
        }
    }

    private fun announce(count: Int, scope: CoroutineScope) {
        announcement?.cancel()
        announcement = scope.launch {
            _message.value = SarcasmCatalogue.lineFor(count, clockHour())
            delay(MESSAGE_DURATION_MS)
            _message.value = null
        }
    }

    companion object {
        /**
         * Long enough that the line is comfortably readable rather than
         * glimpsed -- the previous 3.6s included the expand animation, leaving
         * well under two seconds of settled text.
         */
        const val MESSAGE_DURATION_MS = 4_000L
    }
}
