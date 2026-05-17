package com.capstone.mobilemeasure.data.session

import com.capstone.mobilemeasure.arcore.FloorCalibrationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 측정 시작 시 결정된 floor calibration. ViewModel/Repository 사이에 공유한다.
 */
object ActiveCalibration {
    private val _state = MutableStateFlow<FloorCalibrationState?>(null)
    val state: StateFlow<FloorCalibrationState?> = _state.asStateFlow()

    val current: FloorCalibrationState? get() = _state.value

    fun publish(calibration: FloorCalibrationState) {
        _state.value = calibration
    }

    fun clear() {
        _state.value = null
    }
}
