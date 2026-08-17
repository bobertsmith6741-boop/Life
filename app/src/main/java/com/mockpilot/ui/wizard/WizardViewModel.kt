package com.mockpilot.ui.wizard

import androidx.lifecycle.ViewModel
import com.mockpilot.scheduler.ScheduleManager
import com.mockpilot.selftest.SelfTestReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val reader: SelfTestReader,
    private val scheduleManager: ScheduleManager,
) : ViewModel() {

    private val _mockSelected = MutableStateFlow(false)
    val mockSelected: StateFlow<Boolean> = _mockSelected.asStateFlow()

    private val _exactAlarms = MutableStateFlow(true)
    val exactAlarms: StateFlow<Boolean> = _exactAlarms.asStateFlow()

    /** Re-poll the live permission state; called on a timer by the screen. */
    fun refresh() {
        _mockSelected.value = reader.isMockAppSelected()
        _exactAlarms.value = scheduleManager.canScheduleExactAlarms()
    }
}
