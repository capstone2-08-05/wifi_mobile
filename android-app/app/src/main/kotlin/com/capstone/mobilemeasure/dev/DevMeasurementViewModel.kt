package com.capstone.mobilemeasure.dev

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.mobilemeasure.BuildConfig
import com.capstone.mobilemeasure.data.remote.NetworkClient
import com.capstone.mobilemeasure.data.remote.dto.CalibrationDto
import com.capstone.mobilemeasure.data.remote.dto.CreateMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.DeviceInfoDto
import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto
import com.capstone.mobilemeasure.data.remote.dto.MeasureContextDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementSessionResponseDto
import com.capstone.mobilemeasure.data.repository.MeasurementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevMeasureUiState(
    val token: String = "",
    val isFetchingContext: Boolean = false,
    val isCreatingSession: Boolean = false,
    val context: MeasureContextDto? = null,
    val session: MeasurementSessionResponseDto? = null,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
)

class DevMeasurementViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MeasurementRepository(NetworkClient.measurementApi)

    private val _state = MutableStateFlow(DevMeasureUiState())
    val state: StateFlow<DevMeasureUiState> = _state.asStateFlow()

    fun onTokenChange(token: String) {
        _state.update { it.copy(token = token, errorMessage = null) }
    }

    fun fetchContext() {
        val token = _state.value.token.trim()
        if (token.isEmpty()) {
            _state.update { it.copy(errorMessage = "token이 비어 있음") }
            return
        }
        if (_state.value.isFetchingContext) return

        _state.update { it.copy(isFetchingContext = true, errorMessage = null) }
        appendLog("GET /measurement-links/$token/context")

        viewModelScope.launch {
            repository.getContext(token).fold(
                onSuccess = { ctx ->
                    appendLog(
                        "context ok: project=${ctx.projectId} floor=${ctx.floorId} " +
                            "scene=${ctx.sceneVersionId} asset=${ctx.assetId} " +
                            "floorplan=${ctx.floorplan.url}"
                    )
                    _state.update {
                        it.copy(
                            isFetchingContext = false,
                            context = ctx,
                            session = null,
                        )
                    }
                },
                onFailure = { err ->
                    appendLog("context error: ${err.javaClass.simpleName} ${err.message}")
                    _state.update {
                        it.copy(
                            isFetchingContext = false,
                            errorMessage = err.message ?: "context 조회 실패",
                        )
                    }
                },
            )
        }
    }

    fun createSession() {
        val token = _state.value.context?.token ?: _state.value.token.trim()
        if (token.isEmpty()) {
            _state.update { it.copy(errorMessage = "token이 비어 있음") }
            return
        }
        if (_state.value.isCreatingSession) return

        val request = CreateMeasurementSessionRequest(
            measurementLinkToken = token,
            measurementType = "rssi",
            deviceInfo = DeviceInfoDto(
                model = Build.MODEL,
                os = "Android ${Build.VERSION.RELEASE}",
                appVersion = BuildConfig.VERSION_NAME,
            ),
            calibration = CalibrationDto(
                method = "manual_start_point",
                startFloorPosition = FloorPositionDto(x = 0.0, y = 0.0, z = 1.2),
                initialHeadingDeg = 0.0,
            ),
        )

        _state.update { it.copy(isCreatingSession = true, errorMessage = null) }
        appendLog("POST /measurement-sessions (token=$token)")

        viewModelScope.launch {
            repository.createSession(request).fold(
                onSuccess = { sess ->
                    appendLog(
                        "session ok: id=${sess.id} status=${sess.status} type=${sess.measurementType}"
                    )
                    _state.update {
                        it.copy(
                            isCreatingSession = false,
                            session = sess,
                        )
                    }
                },
                onFailure = { err ->
                    appendLog("session error: ${err.javaClass.simpleName} ${err.message}")
                    _state.update {
                        it.copy(
                            isCreatingSession = false,
                            errorMessage = err.message ?: "session 생성 실패",
                        )
                    }
                },
            )
        }
    }

    fun clear() {
        _state.update {
            it.copy(
                context = null,
                session = null,
                errorMessage = null,
            )
        }
    }

    private fun appendLog(line: String) {
        val ts = System.currentTimeMillis() % 100_000
        val entry = "[$ts] $line"
        _state.update {
            val next = (it.logs + entry).takeLast(LOG_BUFFER_SIZE)
            it.copy(logs = next)
        }
    }

    private companion object {
        const val LOG_BUFFER_SIZE = 100
    }
}
