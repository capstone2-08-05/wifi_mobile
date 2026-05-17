package com.capstone.mobilemeasure.data.session

import com.capstone.mobilemeasure.data.remote.dto.MeasureContextDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dev에서 조회한 측정 context를 Measure 화면에서 참조하기 위한 공유 객체.
 * bounds, coordinate_system 같은 floor 메타데이터를 ViewModel 사이에서 나눠 쓴다.
 */
object ActiveMeasureContext {
    private val _state = MutableStateFlow<MeasureContextDto?>(null)
    val state: StateFlow<MeasureContextDto?> = _state.asStateFlow()

    val current: MeasureContextDto? get() = _state.value

    fun publish(context: MeasureContextDto) {
        _state.value = context
    }

    fun clear() {
        _state.value = null
    }
}
