package com.mockpilot.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockpilot.data.SavedPlace
import com.mockpilot.data.SavedPlaceDao
import com.mockpilot.engine.EngineController
import com.mockpilot.engine.SpoofManager
import com.mockpilot.model.EngineMode
import com.mockpilot.model.MockConfig
import com.mockpilot.model.TeleportPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val savedPlaceDao: SavedPlaceDao,
    private val spoofManager: SpoofManager,
    controller: EngineController,
) : ViewModel() {

    val places: StateFlow<List<SavedPlace>> = savedPlaceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val engineState = controller.state

    fun activate(place: SavedPlace) {
        spoofManager.start(
            MockConfig(
                mode = EngineMode.STATIONARY,
                target = place.toGeoPoint(),
                teleportPolicy = TeleportPolicy.TRAVEL,
                label = place.name,
            ),
        )
    }

    fun stop() = spoofManager.stop()

    fun delete(place: SavedPlace) {
        viewModelScope.launch { savedPlaceDao.delete(place) }
    }
}
