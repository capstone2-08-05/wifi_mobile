package com.capstone.mobilemeasure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.mobilemeasure.data.MeasurementSession
import com.capstone.mobilemeasure.data.RssiSample
import com.capstone.mobilemeasure.data.remote.NetworkClient
import com.capstone.mobilemeasure.data.remote.dto.CompleteMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementPointDto
import com.capstone.mobilemeasure.data.remote.dto.UploadMeasurementPointsRequest
import com.capstone.mobilemeasure.data.repository.MeasurementRepository
import com.capstone.mobilemeasure.data.session.ActiveMeasurementSession
import com.capstone.mobilemeasure.permission.PermissionHelper
import com.capstone.mobilemeasure.wifi.WifiScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID

data class MeasureUiState(
    val isMeasuring: Boolean = false,
    val sessionId: String? = null,
    val sessionFilePath: String? = null,
    val sampleCount: Int = 0,
    val lastSample: RssiSample? = null,
    val recentLogs: List<String> = emptyList(),
    val apiSessionId: String? = null,
    val pendingBufferSize: Int = 0,
    val serverPointsTotal: Int = 0,
    val isUploadingPoints: Boolean = false,
    val isCompleting: Boolean = false,
    val sessionCompleted: Boolean = false,
    val lastUploadInfo: String? = null,
    val completionSummary: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_BUFFER_SIZE = 200
        private const val POINT_BATCH_THRESHOLD = 5
        private const val DEFAULT_FLOOR_Z = 1.2
        private const val SOURCE_TAG = "android_rssi_only"
    }

    private val repository = MeasurementRepository(NetworkClient.measurementApi)

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    private val logs: ArrayDeque<String> = ArrayDeque()
    private var session: MeasurementSession? = null
    private var collectJob: Job? = null
    private var scanner: WifiScanner? = null

    private val pointBuffer: MutableList<MeasurementPointDto> = mutableListOf()
    private var stepIndex: Int = 0

    /** Snapshot of the active backend session id taken at startMeasuring. */
    private var activeApiSessionId: String? = null

    fun startMeasuring() {
        if (_state.value.isMeasuring) return
        if (_state.value.isUploadingPoints || _state.value.isCompleting) {
            appendLog("이전 세션의 업로드/완료 진행 중 — 잠시 후 다시 시도")
            return
        }

        val missing = PermissionHelper.missingPermissions(getApplication())
        if (missing.isNotEmpty()) {
            appendLog("권한 누락: ${missing.joinToString { it.substringAfterLast('.') }}")
            return
        }

        val newSession = try {
            MeasurementSession.start(getApplication())
        } catch (e: Exception) {
            appendLog("Error starting session: ${e.message}")
            return
        }

        val newScanner = WifiScanner(getApplication(), onDiagnostic = ::appendLog)
        session = newSession
        scanner = newScanner

        val heldSession = ActiveMeasurementSession.current
        val apiSession = ActiveMeasurementSession.activeOrNull()
        activeApiSessionId = apiSession?.id
        pointBuffer.clear()
        stepIndex = 0

        when {
            heldSession == null ->
                appendLog("API 세션 없음 — 서버 업로드 비활성, CSV만 저장")
            apiSession == null ->
                appendLog(
                    "API 세션 ${heldSession.id}는 이미 완료됨 — 서버 업로드 비활성, " +
                        "Dev에서 새 세션을 만들어야 다시 업로드 가능"
                )
            else ->
                appendLog("API 세션 활성: ${apiSession.id} (status=${apiSession.status})")
        }

        appendLog("session start: ${newSession.sessionId}")
        _state.update {
            it.copy(
                isMeasuring = true,
                sessionId = newSession.sessionId,
                sessionFilePath = newSession.file.absolutePath,
                sampleCount = 0,
                lastSample = null,
                apiSessionId = apiSession?.id,
                pendingBufferSize = 0,
                serverPointsTotal = 0,
                sessionCompleted = false,
                lastUploadInfo = null,
                completionSummary = null,
            )
        }

        collectJob = viewModelScope.launch {
            newScanner.scanFlow().collect { samples ->
                if (samples.isEmpty()) {
                    return@collect
                }

                samples.forEach { sample ->
                    newSession.append(sample)
                    if (activeApiSessionId != null) {
                        pointBuffer.add(sample.toMeasurementPointDto(stepIndex++))
                    }
                }
                val last = samples.last()
                appendLog(
                    "rssi=${last.rssi}dBm ssid=${last.ssid} bssid=${last.bssid} " +
                        "freq=${last.frequencyMhz}MHz csv=${newSession.totalSamples} " +
                        "buffer=${pointBuffer.size}"
                )
                _state.update {
                    it.copy(
                        sampleCount = newSession.totalSamples,
                        lastSample = last,
                        pendingBufferSize = pointBuffer.size,
                    )
                }

                maybeAutoUpload()
            }
        }
    }

    fun stopMeasuring() {
        if (!_state.value.isMeasuring) return

        collectJob?.cancel()
        collectJob = null

        session?.close()
        session = null

        scanner = null

        appendLog("session stopped (csv 저장 종료)")
        _state.update { it.copy(isMeasuring = false) }

        val sessionToFinalize = activeApiSessionId
        if (sessionToFinalize != null) {
            viewModelScope.launch {
                flushBuffer(sessionId = sessionToFinalize, reason = "stop")
                completeApiSession(sessionId = sessionToFinalize)
            }
        }
    }

    fun markIssue() {
        if (!_state.value.isMeasuring) {
            appendLog("문제 지점 표시는 측정 중일 때만 가능")
            return
        }
        val sample = _state.value.lastSample
        val tag = if (sample != null) {
            "ISSUE marker @ ${sample.bssid} rssi=${sample.rssi}dBm count=${_state.value.sampleCount}"
        } else {
            "ISSUE marker (샘플 없음)"
        }
        appendLog("📍 $tag")
    }

    /** "Upload" 버튼: 측정 종료 후 남은 buffer 재시도 + (필요 시) complete 재시도. */
    fun onUploadRequested() {
        val path = _state.value.sessionFilePath
        if (path == null) {
            appendLog("업로드: 저장된 세션 없음")
            return
        }
        if (_state.value.isMeasuring) {
            appendLog("업로드: 측정 중에는 사용할 수 없음")
            return
        }
        val sessionId = activeApiSessionId
        if (sessionId == null) {
            appendLog("업로드: API 세션 없음 (CSV 경로: $path)")
            return
        }

        viewModelScope.launch {
            if (pointBuffer.isNotEmpty()) {
                flushBuffer(sessionId = sessionId, reason = "manual")
            } else {
                appendLog("업로드: buffer 비어 있음")
            }
            if (!_state.value.sessionCompleted) {
                completeApiSession(sessionId = sessionId)
            }
        }
    }

    override fun onCleared() {
        stopMeasuring()
        super.onCleared()
    }

    private fun appendLog(line: String) {
        val ts = System.currentTimeMillis() % 100_000
        val entry = "[$ts] $line"
        synchronized(logs) {
            logs.addLast(entry)
            while (logs.size > LOG_BUFFER_SIZE) logs.removeFirst()
            _state.update { it.copy(recentLogs = logs.toList()) }
        }
    }

    private fun RssiSample.toMeasurementPointDto(step: Int): MeasurementPointDto =
        MeasurementPointDto(
            clientPointId = UUID.randomUUID().toString(),
            floorPosition = FloorPositionDto(x = 0.0, y = 0.0, z = DEFAULT_FLOOR_Z),
            rssiDbm = rssi.toDouble(),
            apBssid = bssid,
            apSsid = ssid,
            channel = null,
            frequencyMhz = frequencyMhz,
            timestampAtPoint = Instant.ofEpochMilli(timestampMs).toString(),
            arTrackingState = null,
            arConfidence = null,
            stepIndex = step,
            metadataJson = mapOf("source" to SOURCE_TAG),
        )

    private fun maybeAutoUpload() {
        val sessionId = activeApiSessionId ?: return
        if (_state.value.isUploadingPoints) return
        if (pointBuffer.size < POINT_BATCH_THRESHOLD) return
        viewModelScope.launch { flushBuffer(sessionId = sessionId, reason = "threshold") }
    }

    /** Drain the buffer in batches. Failed batches stay in buffer. */
    private suspend fun flushBuffer(sessionId: String, reason: String) {
        if (_state.value.isUploadingPoints) return
        if (pointBuffer.isEmpty()) return

        _state.update { it.copy(isUploadingPoints = true) }
        try {
            while (pointBuffer.isNotEmpty()) {
                val batch = pointBuffer.take(POINT_BATCH_THRESHOLD).toList()
                val batchId = UUID.randomUUID().toString()
                appendLog("POST /points reason=$reason size=${batch.size} batch=$batchId")

                val result = repository.uploadPoints(
                    sessionId = sessionId,
                    request = UploadMeasurementPointsRequest(
                        batchId = batchId,
                        points = batch,
                    ),
                )

                result.fold(
                    onSuccess = { resp ->
                        repeat(batch.size) { pointBuffer.removeAt(0) }
                        val info = "inserted=${resp.inserted} dup=${resp.duplicated} " +
                            "status=${resp.sessionStatus}"
                        appendLog("points ok: $info")
                        _state.update {
                            it.copy(
                                pendingBufferSize = pointBuffer.size,
                                serverPointsTotal = it.serverPointsTotal + resp.inserted,
                                lastUploadInfo = info,
                            )
                        }
                    },
                    onFailure = { err ->
                        appendLog("points error: ${err.javaClass.simpleName} ${err.message}")
                        _state.update {
                            it.copy(
                                pendingBufferSize = pointBuffer.size,
                                lastUploadInfo = "FAIL: ${err.message ?: err.javaClass.simpleName}",
                            )
                        }
                        return@fold
                    },
                )

                if (result.isFailure) break
            }
        } finally {
            _state.update { it.copy(isUploadingPoints = false) }
        }
    }

    private suspend fun completeApiSession(sessionId: String) {
        if (_state.value.sessionCompleted) return
        if (pointBuffer.isNotEmpty()) {
            appendLog("complete 보류: buffer ${pointBuffer.size}개 남음")
            return
        }

        _state.update { it.copy(isCompleting = true) }
        appendLog("POST /complete sessionId=$sessionId")

        val result = repository.completeSession(
            sessionId = sessionId,
            request = CompleteMeasurementSessionRequest(
                endPosition = FloorPositionDto(x = 0.0, y = 0.0, z = DEFAULT_FLOOR_Z),
            ),
        )

        result.fold(
            onSuccess = { resp ->
                val summary = "totalPoints=${resp.totalPoints} duration=${resp.durationSeconds}s " +
                    "ap=${resp.apCount} rssi=[${resp.rssiRange.min}..${resp.rssiRange.max} " +
                    "avg=${resp.rssiRange.avg}] status=${resp.status}"
                appendLog("complete ok: $summary")
                ActiveMeasurementSession.markCompleted(resp.status)
                _state.update {
                    it.copy(
                        isCompleting = false,
                        sessionCompleted = true,
                        completionSummary = summary,
                    )
                }
            },
            onFailure = { err ->
                appendLog("complete error: ${err.javaClass.simpleName} ${err.message}")
                _state.update { it.copy(isCompleting = false) }
            },
        )
    }
}
