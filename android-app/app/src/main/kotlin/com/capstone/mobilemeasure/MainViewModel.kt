package com.capstone.mobilemeasure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.mobilemeasure.arcore.ArCoreSessionManager
import com.capstone.mobilemeasure.arcore.ArPoseSnapshot
import com.capstone.mobilemeasure.arcore.FloorCalibrationState
import com.capstone.mobilemeasure.arcore.FloorPositionMapper
import com.capstone.mobilemeasure.data.MeasurementSession
import com.capstone.mobilemeasure.data.RssiSample
import com.capstone.mobilemeasure.data.remote.NetworkClient
import com.capstone.mobilemeasure.data.remote.dto.CompleteMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.FloorBoundsDto
import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto
import com.capstone.mobilemeasure.data.remote.dto.FloorplanInfoDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementPointDto
import com.capstone.mobilemeasure.data.remote.dto.UploadMeasurementPointsRequest
import com.capstone.mobilemeasure.data.repository.MeasurementRepository
import com.capstone.mobilemeasure.data.session.ActiveCalibration
import com.capstone.mobilemeasure.data.session.ActiveMeasureContext
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

data class CalibrationInputState(
    val startFloorX: String = "0.0",
    val startFloorY: String = "0.0",
    val initialHeadingDeg: String = "0.0",
)

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
    val calibrationInput: CalibrationInputState = CalibrationInputState(),
    val activeCalibration: FloorCalibrationState? = null,
    val arAvailability: ArCoreSessionManager.Availability =
        ArCoreSessionManager.Availability.UNKNOWN,
    val arTrackingState: String? = null,
    val currentFloorPosition: FloorPositionDto? = null,
    val lastUploadedFloorPosition: FloorPositionDto? = null,
    val floorBounds: FloorBoundsDto? = null,
    val floorplan: FloorplanInfoDto? = null,
    val isOutOfBounds: Boolean = false,
    val outOfBoundsCount: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_BUFFER_SIZE = 200
        private const val POINT_BATCH_THRESHOLD = 5
        private const val SOURCE_TAG = "android_arcore_rssi"
        private const val POSITION_MODE_AR = "arcore_calibrated"
        private const val POSITION_MODE_FALLBACK = "calibration_only"
    }

    private val repository = MeasurementRepository(NetworkClient.measurementApi)

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    val arSessionManager: ArCoreSessionManager = ArCoreSessionManager(
        onError = { msg -> appendLog("AR: $msg") },
    )

    private val logs: ArrayDeque<String> = ArrayDeque()
    private var session: MeasurementSession? = null
    private var collectJob: Job? = null
    private var poseObserveJob: Job? = null
    private var availabilityObserveJob: Job? = null
    private var scanner: WifiScanner? = null

    private val pointBuffer: MutableList<MeasurementPointDto> = mutableListOf()
    private var stepIndex: Int = 0

    private var activeApiSessionId: String? = null

    private var contextObserveJob: Job? = null

    init {
        availabilityObserveJob = viewModelScope.launch {
            arSessionManager.availability.collect { avail ->
                _state.update { it.copy(arAvailability = avail) }
            }
        }
        poseObserveJob = viewModelScope.launch {
            arSessionManager.latestPose.collect { pose ->
                onPoseUpdated(pose)
            }
        }
        contextObserveJob = viewModelScope.launch {
            ActiveMeasureContext.state.collect { ctx ->
                _state.update {
                    it.copy(
                        floorBounds = ctx?.bounds,
                        floorplan = ctx?.floorplan,
                    )
                }
            }
        }
    }

    fun onCalibrationFieldChange(
        startFloorX: String? = null,
        startFloorY: String? = null,
        initialHeadingDeg: String? = null,
    ) {
        _state.update {
            val cur = it.calibrationInput
            it.copy(
                calibrationInput = cur.copy(
                    startFloorX = startFloorX ?: cur.startFloorX,
                    startFloorY = startFloorY ?: cur.startFloorY,
                    initialHeadingDeg = initialHeadingDeg ?: cur.initialHeadingDeg,
                ),
            )
        }
        // 측정 시작 전이라도 Dev 화면에서 session 생성 시 calibration 값을 가져갈 수 있도록
        // 입력이 valid해질 때마다 draft를 publish 한다. initialAr*은 측정 시작 시점에 갱신됨.
        if (!_state.value.isMeasuring) {
            parseCalibrationInput()?.let { ActiveCalibration.publish(it) }
        }
    }

    /**
     * 도면 위에서 사용자가 드래그로 시작 위치 + heading을 직접 지정했을 때 호출된다.
     * 텍스트 입력 필드도 같이 갱신해서 두 입력 경로가 항상 동기화되도록 한다.
     * headingDeg=null 이면 (드래그 거리가 짧았던 경우) heading은 기존값 유지하고 위치만 갱신.
     */
    fun onCalibrationPickedFromMap(floorX: Double, floorY: Double, headingDeg: Double?) {
        if (_state.value.isMeasuring) return
        _state.update {
            val cur = it.calibrationInput
            it.copy(
                calibrationInput = cur.copy(
                    startFloorX = formatCoord(floorX),
                    startFloorY = formatCoord(floorY),
                    initialHeadingDeg = headingDeg?.let(::formatHeading) ?: cur.initialHeadingDeg,
                ),
            )
        }
        parseCalibrationInput()?.let { ActiveCalibration.publish(it) }
        appendLog(
            "도면 picker: start=(${"%.2f".format(floorX)}, ${"%.2f".format(floorY)})" +
                (headingDeg?.let { " h=${"%.1f".format(normalizeHeading(it))}°" } ?: "")
        )
    }

    private fun formatCoord(v: Double): String = "%.2f".format(v)
    private fun formatHeading(v: Double): String = "%.1f".format(normalizeHeading(v))

    /** atan2 결과는 (-180, 180] 범위라 사용자에게는 [0, 360) 범위가 더 자연스럽다. */
    private fun normalizeHeading(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

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

        val parsedCalibration = parseCalibrationInput() ?: run {
            appendLog("calibration 입력값을 확인하세요 (숫자 형식)")
            return
        }

        val initialPose = arSessionManager.latestPose.value
        if (initialPose == null) {
            appendLog("AR pose 없음 — 카메라 프리뷰가 트래킹 시작될 때까지 기다리세요")
        } else if (!initialPose.isTracking) {
            appendLog("AR tracking=${initialPose.trackingState} — 시작하지만 좌표 정확도 낮을 수 있음")
        }

        val calibration = parsedCalibration.copy(
            initialArX = initialPose?.tx?.toDouble() ?: 0.0,
            initialArY = initialPose?.ty?.toDouble() ?: 0.0,
            initialArZ = initialPose?.tz?.toDouble() ?: 0.0,
        )
        ActiveCalibration.publish(calibration)

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

        appendLog(
            "calibration: start=(${calibration.startFloorX}, ${calibration.startFloorY}) " +
                "heading=${calibration.initialHeadingDeg}° " +
                "initAr=(${"%.3f".format(calibration.initialArX)}, " +
                "${"%.3f".format(calibration.initialArZ)})"
        )
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
                activeCalibration = calibration,
                currentFloorPosition = currentFloorPositionOrStart(calibration, initialPose),
                lastUploadedFloorPosition = null,
                outOfBoundsCount = 0,
                isOutOfBounds = false,
            )
        }

        collectJob = viewModelScope.launch {
            newScanner.scanFlow().collect { samples ->
                if (samples.isEmpty()) {
                    return@collect
                }

                val cal = ActiveCalibration.current
                val bounds = _state.value.floorBounds
                var oobAdded = 0
                samples.forEach { sample ->
                    newSession.append(sample)
                    if (activeApiSessionId != null && cal != null) {
                        val pose = arSessionManager.latestPose.value
                        val dto = buildPointDto(sample, cal, pose, stepIndex++)
                        if (bounds != null && !FloorPositionMapper.isInsideBounds(dto.floorPosition, bounds)) {
                            oobAdded += 1
                        }
                        pointBuffer.add(dto)
                    }
                }
                if (oobAdded > 0) {
                    appendLog(
                        "⚠ bounds 밖 point $oobAdded 개 — pos=" +
                            (_state.value.currentFloorPosition?.let {
                                "(${"%.2f".format(it.x)}, ${"%.2f".format(it.y)})"
                            } ?: "?") +
                            " bounds=(${bounds?.minX}..${bounds?.maxX}, ${bounds?.minY}..${bounds?.maxY})"
                    )
                    _state.update {
                        it.copy(outOfBoundsCount = it.outOfBoundsCount + oobAdded)
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
        val pos = _state.value.currentFloorPosition
        val tag = if (sample != null) {
            "ISSUE marker @ ${sample.bssid} rssi=${sample.rssi}dBm count=${_state.value.sampleCount}" +
                (pos?.let { " pos=(${"%.2f".format(it.x)}, ${"%.2f".format(it.y)})" } ?: "")
        } else {
            "ISSUE marker (샘플 없음)"
        }
        appendLog("📍 $tag")
    }

    /** Floorplan presigned URL 만료 등으로 재조회가 필요할 때 호출. */
    fun refreshContext() {
        val token = ActiveMeasureContext.current?.token
        if (token.isNullOrBlank()) {
            appendLog("도면 새로고침: token 없음 — Dev API에서 Context 조회 필요")
            return
        }
        appendLog("도면 새로고침: GET /measurement-links/$token/context")
        viewModelScope.launch {
            repository.getContext(token).fold(
                onSuccess = { ctx ->
                    ActiveMeasureContext.publish(ctx)
                    appendLog("도면 새로고침 ok: url=${ctx.floorplan.url?.take(60) ?: "-"}…")
                },
                onFailure = { err ->
                    appendLog("도면 새로고침 실패: ${err.javaClass.simpleName} ${err.message}")
                },
            )
        }
    }

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
        availabilityObserveJob?.cancel()
        poseObserveJob?.cancel()
        contextObserveJob?.cancel()
        arSessionManager.close()
        super.onCleared()
    }

    private fun parseCalibrationInput(): FloorCalibrationState? {
        val input = _state.value.calibrationInput
        val x = input.startFloorX.toDoubleOrNull() ?: return null
        val y = input.startFloorY.toDoubleOrNull() ?: return null
        val h = input.initialHeadingDeg.toDoubleOrNull() ?: return null
        return FloorCalibrationState(
            startFloorX = x,
            startFloorY = y,
            initialHeadingDeg = h,
            initialArX = 0.0,
            initialArY = 0.0,
            initialArZ = 0.0,
        )
    }

    private fun onPoseUpdated(pose: ArPoseSnapshot?) {
        val cal = ActiveCalibration.current ?: _state.value.activeCalibration
        val tracking = pose?.trackingState
        val floorPos = if (cal != null && pose != null) {
            FloorPositionMapper.map(cal, pose)
        } else if (cal != null) {
            cal.toStartFloorPositionDto()
        } else {
            null
        }
        val bounds = _state.value.floorBounds
        val outside = floorPos != null && bounds != null &&
            !FloorPositionMapper.isInsideBounds(floorPos, bounds)
        _state.update {
            it.copy(
                arTrackingState = tracking,
                currentFloorPosition = floorPos ?: it.currentFloorPosition,
                isOutOfBounds = outside,
            )
        }
    }

    private fun currentFloorPositionOrStart(
        calibration: FloorCalibrationState,
        pose: ArPoseSnapshot?,
    ): FloorPositionDto = if (pose != null) {
        FloorPositionMapper.map(calibration, pose)
    } else {
        calibration.toStartFloorPositionDto()
    }

    private fun buildPointDto(
        sample: RssiSample,
        calibration: FloorCalibrationState,
        pose: ArPoseSnapshot?,
        step: Int,
    ): MeasurementPointDto {
        val floorPos = if (pose != null) {
            FloorPositionMapper.map(calibration, pose)
        } else {
            calibration.toStartFloorPositionDto()
        }
        val mode = if (pose != null) POSITION_MODE_AR else POSITION_MODE_FALLBACK
        val metadata: Map<String, Any?> = buildMap {
            put("source", SOURCE_TAG)
            put("position_mode", mode)
            if (pose != null) {
                put(
                    "ar_pose",
                    mapOf(
                        "tx" to pose.tx,
                        "ty" to pose.ty,
                        "tz" to pose.tz,
                        "qx" to pose.qx,
                        "qy" to pose.qy,
                        "qz" to pose.qz,
                        "qw" to pose.qw,
                        "tracking_state" to pose.trackingState,
                        "timestamp_ns" to pose.timestampNs,
                    ),
                )
            }
            put(
                "calibration",
                mapOf(
                    "start_floor_x" to calibration.startFloorX,
                    "start_floor_y" to calibration.startFloorY,
                    "start_floor_z" to calibration.startFloorZ,
                    "initial_heading_deg" to calibration.initialHeadingDeg,
                    "initial_ar_x" to calibration.initialArX,
                    "initial_ar_y" to calibration.initialArY,
                    "initial_ar_z" to calibration.initialArZ,
                ),
            )
        }
        return MeasurementPointDto(
            clientPointId = UUID.randomUUID().toString(),
            floorPosition = floorPos,
            rssiDbm = sample.rssi.toDouble(),
            apBssid = sample.bssid,
            apSsid = sample.ssid,
            channel = null,
            frequencyMhz = sample.frequencyMhz,
            timestampAtPoint = Instant.ofEpochMilli(sample.timestampMs).toString(),
            arTrackingState = pose?.trackingState,
            arConfidence = null,
            stepIndex = step,
            metadataJson = metadata,
        )
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

    private fun maybeAutoUpload() {
        val sessionId = activeApiSessionId ?: return
        if (_state.value.isUploadingPoints) return
        if (pointBuffer.size < POINT_BATCH_THRESHOLD) return
        viewModelScope.launch { flushBuffer(sessionId = sessionId, reason = "threshold") }
    }

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
                        val lastFloor = batch.lastOrNull()?.floorPosition
                        repeat(batch.size) { pointBuffer.removeAt(0) }
                        val info = "inserted=${resp.inserted} dup=${resp.duplicated} " +
                            "status=${resp.sessionStatus}"
                        appendLog("points ok: $info")
                        _state.update {
                            it.copy(
                                pendingBufferSize = pointBuffer.size,
                                serverPointsTotal = it.serverPointsTotal + resp.inserted,
                                lastUploadInfo = info,
                                lastUploadedFloorPosition = lastFloor ?: it.lastUploadedFloorPosition,
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

        val endPos = _state.value.currentFloorPosition
            ?: _state.value.activeCalibration?.toStartFloorPositionDto()
            ?: FloorPositionDto(x = 0.0, y = 0.0, z = FloorCalibrationState.DEFAULT_FLOOR_Z)

        val result = repository.completeSession(
            sessionId = sessionId,
            request = CompleteMeasurementSessionRequest(
                endPosition = endPos,
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
