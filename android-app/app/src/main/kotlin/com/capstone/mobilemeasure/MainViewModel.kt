package com.capstone.mobilemeasure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.mobilemeasure.data.MeasurementSession
import com.capstone.mobilemeasure.data.RssiSample
import com.capstone.mobilemeasure.permission.PermissionHelper
import com.capstone.mobilemeasure.wifi.WifiScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class MeasureUiState(
    val isMeasuring: Boolean = false,
    val sessionId: String? = null,
    val sessionFilePath: String? = null,
    val sampleCount: Int = 0,
    val lastSample: RssiSample? = null,
    val recentLogs: List<String> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_BUFFER_SIZE = 200
    }

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    private val logs: ArrayDeque<String> = ArrayDeque()
    private var session: MeasurementSession? = null
    private var collectJob: Job? = null
    private var scanner: WifiScanner? = null

    fun startMeasuring() {
        if (_state.value.isMeasuring) return

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

        appendLog("session start: ${newSession.sessionId}")
        _state.update {
            it.copy(
                isMeasuring = true,
                sessionId = newSession.sessionId,
                sessionFilePath = newSession.file.absolutePath,
                sampleCount = 0,
                lastSample = null,
            )
        }

        collectJob = viewModelScope.launch {
            newScanner.scanFlow().collect { samples ->
                if (samples.isEmpty()) {
                    // 사유는 WifiScanner.onDiagnostic에서 이미 한 번 기록함.
                    return@collect
                }

                samples.forEach { newSession.append(it) }
                val last = samples.last()
                appendLog(
                    "rssi=${last.rssi}dBm ssid=${last.ssid} bssid=${last.bssid} " +
                            "freq=${last.frequencyMhz}MHz count=${newSession.totalSamples}"
                )
                _state.update {
                    it.copy(
                        sampleCount = newSession.totalSamples,
                        lastSample = last,
                    )
                }
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

        appendLog("session stopped")
        _state.update { it.copy(isMeasuring = false) }
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

    fun onUploadRequested() {
        val path = _state.value.sessionFilePath
        if (path == null) {
            appendLog("업로드: 저장된 세션 없음")
        } else {
            appendLog("업로드 (추후 구현). CSV 경로: $path")
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
}
