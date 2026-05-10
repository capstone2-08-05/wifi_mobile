package com.capstone.mobilemeasure.data.session

import com.capstone.mobilemeasure.data.remote.dto.MeasurementSessionResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveMeasurementSession {

    private val _session = MutableStateFlow<MeasurementSessionResponseDto?>(null)
    val session: StateFlow<MeasurementSessionResponseDto?> = _session.asStateFlow()

    val current: MeasurementSessionResponseDto? get() = _session.value

    fun publish(session: MeasurementSessionResponseDto) {
        _session.value = session
    }

    fun markCompleted(status: String) {
        _session.value = _session.value?.copy(status = status)
    }

    fun clear() {
        _session.value = null
    }
}
