package com.mockpilot.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockpilot.data.SavedPlace
import com.mockpilot.data.SavedPlaceDao
import com.mockpilot.data.geocode.GeocodeResult
import com.mockpilot.data.geocode.GeocodingRepository
import com.mockpilot.engine.EngineController
import com.mockpilot.engine.SpoofManager
import com.mockpilot.model.EngineMode
import com.mockpilot.model.EngineState
import com.mockpilot.model.GeoPoint
import com.mockpilot.model.MockConfig
import com.mockpilot.model.TeleportPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val spoofManager: SpoofManager,
    private val controller: EngineController,
    private val geocoding: GeocodingRepository,
    private val savedPlaceDao: SavedPlaceDao,
) : ViewModel() {

    val engineState: StateFlow<EngineState> = controller.state
    val lastSample = controller.lastSample
    val startedAt = controller.startedAt
    val events = controller.events

    private val _selected = MutableStateFlow<GeoPoint?>(null)
    val selected: StateFlow<GeoPoint?> = _selected.asStateFlow()

    private val _selectedLabel = MutableStateFlow("Dropped pin")
    val selectedLabel: StateFlow<String> = _selectedLabel.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val results: StateFlow<List<GeocodeResult>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _teleportPolicy = MutableStateFlow(TeleportPolicy.TRAVEL)
    val teleportPolicy: StateFlow<TeleportPolicy> = _teleportPolicy.asStateFlow()

    val savedPlaces: StateFlow<List<SavedPlace>> = savedPlaceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRunning: Boolean get() = controller.isRunning

    fun onQueryChange(q: String) { _query.value = q }

    fun setTeleportPolicy(policy: TeleportPolicy) { _teleportPolicy.value = policy }

    fun search() {
        val q = _query.value
        if (q.isBlank()) return
        viewModelScope.launch {
            _searching.value = true
            _results.value = geocoding.search(q)
            _searching.value = false
        }
    }

    fun pick(result: GeocodeResult) {
        _selected.value = result.point
        _selectedLabel.value = result.label.substringBefore(",").take(40)
        _results.value = emptyList()
    }

    fun dropPin(point: GeoPoint) {
        _selected.value = point
        _selectedLabel.value = "Dropped pin"
        // If a session is live, retarget immediately (teleport guard decides how to move there).
        if (controller.isRunning) retarget(point)
    }

    fun selectSaved(place: SavedPlace) {
        _selected.value = place.toGeoPoint()
        _selectedLabel.value = place.name
    }

    fun start() {
        val point = _selected.value ?: return
        spoofManager.start(buildConfig(point))
    }

    private fun retarget(point: GeoPoint) {
        spoofManager.retarget(buildConfig(point))
    }

    private fun buildConfig(point: GeoPoint) = MockConfig(
        mode = EngineMode.STATIONARY,
        target = point,
        updateRateHz = 1.0,
        teleportPolicy = _teleportPolicy.value,
        nightIdleEnabled = true,
        label = _selectedLabel.value,
    )

    fun stop() {
        spoofManager.stop()
    }

    fun savePlace(name: String) {
        val point = _selected.value ?: return
        viewModelScope.launch {
            savedPlaceDao.insert(
                SavedPlace(
                    name = name.ifBlank { _selectedLabel.value },
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitude = point.altitude,
                ),
            )
        }
    }
}
