package com.mockpilot.ui.selftest

import androidx.lifecycle.ViewModel
import com.mockpilot.selftest.SelfTestReader
import com.mockpilot.selftest.SelfTestReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SelfTestViewModel @Inject constructor(
    private val reader: SelfTestReader,
) : ViewModel() {

    private val _report = MutableStateFlow<SelfTestReport?>(null)
    val report: StateFlow<SelfTestReport?> = _report.asStateFlow()

    private val _mockSelected = MutableStateFlow<Boolean?>(null)
    val mockSelected: StateFlow<Boolean?> = _mockSelected.asStateFlow()

    fun run() {
        _mockSelected.value = reader.isMockAppSelected()
        _report.value = reader.run()
    }
}
