package com.mockpilot.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockpilot.data.SavedPlace
import com.mockpilot.data.SavedPlaceDao
import com.mockpilot.data.ScheduleDao
import com.mockpilot.data.ScheduleEntity
import com.mockpilot.scheduler.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao,
    savedPlaceDao: SavedPlaceDao,
    private val scheduleManager: ScheduleManager,
) : ViewModel() {

    val schedules: StateFlow<List<ScheduleEntity>> = scheduleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val places: StateFlow<List<SavedPlace>> = savedPlaceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun canScheduleExactAlarms(): Boolean = scheduleManager.canScheduleExactAlarms()

    fun add(
        label: String,
        place: SavedPlace,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        daysMask: Int,
    ) {
        viewModelScope.launch {
            scheduleDao.upsert(
                ScheduleEntity(
                    label = label.ifBlank { "At ${place.name}" },
                    placeName = place.name,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    daysMask = daysMask,
                ),
            )
            scheduleManager.sync()
        }
    }

    fun toggle(schedule: ScheduleEntity) {
        viewModelScope.launch {
            scheduleDao.upsert(schedule.copy(enabled = !schedule.enabled))
            scheduleManager.sync()
        }
    }

    fun delete(schedule: ScheduleEntity) {
        viewModelScope.launch {
            scheduleDao.delete(schedule)
            scheduleManager.sync()
        }
    }
}
