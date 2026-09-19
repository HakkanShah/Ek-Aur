package com.ekaur.android.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live state of the accessibility service, in terms a person can read out.
 *
 * Answers "why is it not counting?" without any tooling: connected or not, when
 * the last event arrived, and what the detector currently thinks it is looking
 * at.
 */
class ServiceStatus {

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastEventAtMs = MutableStateFlow(0L)
    val lastEventAtMs: StateFlow<Long> = _lastEventAtMs.asStateFlow()

    private val _lastEventPackage = MutableStateFlow<String?>(null)
    val lastEventPackage: StateFlow<String?> = _lastEventPackage.asStateFlow()

    private val _detectorState = MutableStateFlow("Idle")
    val detectorState: StateFlow<String> = _detectorState.asStateFlow()

    private val _eventsSeen = MutableStateFlow(0L)
    val eventsSeen: StateFlow<Long> = _eventsSeen.asStateFlow()

    fun onConnected() {
        _connected.value = true
    }

    fun onDisconnected() {
        _connected.value = false
        _detectorState.value = "Idle"
    }

    fun onEvent(packageName: String, atMs: Long, state: String) {
        _lastEventAtMs.value = atMs
        _lastEventPackage.value = packageName
        _detectorState.value = state
        _eventsSeen.value += 1
    }
}
