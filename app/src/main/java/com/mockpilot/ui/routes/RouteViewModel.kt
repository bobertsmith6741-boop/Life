package com.mockpilot.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockpilot.data.RouteDao
import com.mockpilot.data.RouteEntity
import com.mockpilot.engine.EngineController
import com.mockpilot.engine.SpoofManager
import com.mockpilot.model.EngineMode
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
class RouteViewModel @Inject constructor(
    private val routeDao: RouteDao,
    private val spoofManager: SpoofManager,
    controller: EngineController,
) : ViewModel() {

    val engineState = controller.state
    val lastSample = controller.lastSample
    val startedAt = controller.startedAt
    val isRunning: Boolean get() = spoofManager.controllerRef.isRunning

    private val _waypoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val waypoints: StateFlow<List<GeoPoint>> = _waypoints.asStateFlow()

    private val _targetSpeed = MutableStateFlow(8.0)
    val targetSpeed: StateFlow<Double> = _targetSpeed.asStateFlow()

    private val _loop = MutableStateFlow(false)
    val loop: StateFlow<Boolean> = _loop.asStateFlow()

    val savedRoutes: StateFlow<List<RouteEntity>> = routeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWaypoint(point: GeoPoint) { _waypoints.value = _waypoints.value + point }
    fun removeLast() { _waypoints.value = _waypoints.value.dropLast(1) }
    fun clear() { _waypoints.value = emptyList() }
    fun setSpeed(mps: Double) { _targetSpeed.value = mps }
    fun setLoop(loop: Boolean) { _loop.value = loop }

    fun preview() {
        val wps = _waypoints.value
        if (wps.size < 2) return
        spoofManager.start(
            MockConfig(
                mode = EngineMode.ROUTE,
                target = wps.first(),
                route = wps,
                targetSpeedMps = _targetSpeed.value,
                loopRoute = _loop.value,
                teleportPolicy = TeleportPolicy.TRAVEL,
                label = "Route (${wps.size} pts)",
            ),
        )
    }

    fun stop() = spoofManager.stop()

    fun save(name: String) {
        val wps = _waypoints.value
        if (wps.size < 2) return
        viewModelScope.launch {
            routeDao.upsert(
                RouteEntity(
                    name = name.ifBlank { "Route ${wps.size}pts" },
                    waypoints = wps,
                    targetSpeedMps = _targetSpeed.value,
                    loop = _loop.value,
                ),
            )
        }
    }

    fun load(route: RouteEntity) {
        _waypoints.value = route.waypoints
        _targetSpeed.value = route.targetSpeedMps
        _loop.value = route.loop
    }

    fun delete(route: RouteEntity) {
        viewModelScope.launch { routeDao.delete(route) }
    }
}
