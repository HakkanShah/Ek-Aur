package com.ekaur.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ekaur.android.data.repo.DayClock
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Today's local date, which rolls over at midnight.
 *
 * Screens key their "today" queries on this, so an app left open overnight
 * starts a fresh day instead of showing yesterday's number as today's. It
 * also re-reads on resume, since a phone asleep through midnight may never run
 * the timer on time.
 */
@Composable
fun rememberToday(): String {
    val clock = remember { DayClock() }
    var today by remember { mutableStateOf(clock.today()) }
    LaunchedEffect(today) {
        val now = ZonedDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay(Duration.between(now, midnight).toMillis() + 1_000)
        today = clock.today()
    }
    LifecycleResumeEffect(Unit) {
        today = clock.today()
        onPauseOrDispose { }
    }
    return today
}
