package com.capstone.mobilemeasure.data.session

import com.capstone.mobilemeasure.data.remote.dto.MeasurementSessionResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveMeasurementSession {

    const val STATUS_IN_PROGRESS = "in_progress"
    const val STATUS_COMPLETED = "completed"

    private val _session = MutableStateFlow<MeasurementSessionResponseDto?>(null)
    val session: StateFlow<MeasurementSessionResponseDto?> = _session.asStateFlow()

    /** Always returns whatever is currently held (may be completed). For display only. */
    val current: MeasurementSessionResponseDto? get() = _session.value

    /** Returns the session only if it is still usable (not completed). */
    fun activeOrNull(): MeasurementSessionResponseDto? =
        _session.value?.takeIf { it.status != STATUS_COMPLETED }

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
