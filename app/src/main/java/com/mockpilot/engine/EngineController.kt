package com.mockpilot.engine

import com.mockpilot.model.EngineEvent
import com.mockpilot.model.EngineState
import com.mockpilot.model.LocationSample
import com.mockpilot.model.MockConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide bridge between the UI and the [MockLocationService]. The UI writes the desired
 * [MockConfig] and reads back live state; the service consumes the config and publishes samples.
 * Held as a Hilt @Singleton so both sides see the same instance.
 */
@Singleton
class EngineController @Inject constructor() {

    private val _desiredConfig = MutableStateFlow<MockConfig?>(null)
    val desiredConfig: StateFlow<MockConfig?> = _desiredConfig.asStateFlow()

    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _sample = MutableStateFlow<LocationSample?>(null)
    val lastSample: StateFlow<LocationSample?> = _sample.asStateFlow()

    private val _startedAt = MutableStateFlow<Long?>(null)
    val startedAt: StateFlow<Long?> = _startedAt.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    val isRunning: Boolean get() = _state.value !is EngineState.Stopped

    // ---- UI-facing ----

    /** Set (or update) what the engine should emit. The service picks this up on its next tick. */
    fun setDesired(config: MockConfig) {
        _desiredConfig.value = config
    }

    fun clearDesired() {
        _desiredConfig.value = null
    }

    // ---- Service-facing ----

    fun publishState(state: EngineState) {
        _state.value = state
    }

    fun publishSample(sample: LocationSample) {
        _sample.value = sample
    }

    fun markStarted(atMillis: Long) {
        _startedAt.value = atMillis
    }

    fun markStopped() {
        _state.value = EngineState.Stopped
        _startedAt.value = null
    }

    fun emitEvent(event: EngineEvent) {
        _events.tryEmit(event)
    }
}
