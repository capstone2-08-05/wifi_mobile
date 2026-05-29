package com.capstone.mobilemeasure.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.mobilemeasure.CalibrationInputState
import com.capstone.mobilemeasure.MeasureUiState
import com.capstone.mobilemeasure.arcore.ArCameraPreview
import com.capstone.mobilemeasure.arcore.ArCoreSessionManager
import com.capstone.mobilemeasure.arcore.findActivity
import com.capstone.mobilemeasure.data.MeasurementPurpose
import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto
import com.capstone.mobilemeasure.qr.QrScanner

private val ScreenBg = Color(0xFFF6F7FB)
private val Highlight = Color(0xFF6FE0C2)
private val Rose = Color(0xFFE53E5C)
private val RoseBg = Color(0xFFFFE4E6)
private val Subdued = Color(0xFF6B7280)
private val DividerGrey = Color(0xFFE5E7EB)
private val Ink = Color(0xFF111827)
private val Accent = Color(0xFF3B82F6)

@Composable
fun MeasureScreen(
    state: MeasureUiState,
    arSessionManager: ArCoreSessionManager,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMarkIssue: () -> Unit,
    onUpload: () -> Unit,
    onPurposeSelected: (MeasurementPurpose) -> Unit,
    onCalibrationFieldChange: (startFloorX: String?, startFloorY: String?, headingDeg: String?) -> Unit,
    onRefreshContext: () -> Unit,
    onCalibrationPickedFromMap: (floorX: Double, floorY: Double, headingDeg: Double?) -> Unit,
    onScannedToken: (String) -> Unit,
    onScanError: (String) -> Unit,
    onScanInstallProgress: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContextHeader(
            state = state,
            onScannedToken = onScannedToken,
            onScanError = onScanError,
            onScanInstallProgress = onScanInstallProgress,
        )

        state.errorMessage?.let { msg ->
            ErrorBanner(message = msg, onDismiss = onDismissError)
        }

        Text(
            text = if (state.isMeasuring) {
                "매장을 천천히 걸어다니며 구석구석을\n측정하고 있습니다."
            } else {
                "시작 위치/방향을 입력하고 카메라가\nAR 추적 중인지 확인한 뒤 측정을 시작하세요."
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Subdued,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        ArPreviewCard(arSessionManager = arSessionManager, state = state)

        MeasurementPurposeCard(
            purpose = state.measurementPurpose,
            isMeasuring = state.isMeasuring,
            onPurposeSelected = onPurposeSelected,
        )

        val startPos = state.activeCalibration?.let {
            FloorPositionDto(x = it.startFloorX, y = it.startFloorY, z = it.startFloorZ)
        } ?: run {
            val x = state.calibrationInput.startFloorX.toDoubleOrNull()
            val y = state.calibrationInput.startFloorY.toDoubleOrNull()
            if (x != null && y != null) FloorPositionDto(x = x, y = y, z = 1.2) else null
        }
        val headingDeg = state.activeCalibration?.initialHeadingDeg
            ?: state.calibrationInput.initialHeadingDeg.toDoubleOrNull()

        FloorplanCard(
            floorplan = state.floorplan,
            bounds = state.floorBounds,
            currentPosition = state.currentFloorPosition,
            startPosition = startPos,
            headingDeg = headingDeg,
            isOutOfBounds = state.isOutOfBounds,
            editable = !state.isMeasuring,
            onRefresh = onRefreshContext,
            onCalibrationPicked = onCalibrationPickedFromMap,
        )

        if (!state.isMeasuring) {
            CalibrationInputCard(
                input = state.calibrationInput,
                onFieldChange = onCalibrationFieldChange,
            )
        }

        FloorPositionCard(state = state)

        WifiStatusCard(state)

        MetricsRow(state)

        ApiUploadStatusCard(state)

        ActionRow(
            isMeasuring = state.isMeasuring,
            purpose = state.measurementPurpose,
            canUpload = state.sessionId != null,
            onStart = onStart,
            onStop = onStop,
            onUpload = onUpload,
        )

        if (state.isMeasuring) {
            MarkIssueLink(onMarkIssue)
        } else {
            Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun ArPreviewCard(arSessionManager: ArCoreSessionManager, state: MeasureUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.arAvailability) {
                ArCoreSessionManager.Availability.UNSUPPORTED -> {
                    UnsupportedArOverlay()
                }
                else -> {
                    ArCameraPreview(
                        sessionManager = arSessionManager,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ArStatusOverlay(state)
                }
            }
        }
    }
}

@Composable
private fun UnsupportedArOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "이 기기는 ARCore를 지원하지 않습니다.\n시작 좌표만으로 측정합니다.",
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MeasurementPurposeCard(
    purpose: MeasurementPurpose,
    isMeasuring: Boolean,
    onPurposeSelected: (MeasurementPurpose) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMeasuring) Color(0xFFEFF6FF) else Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isMeasuring) Accent else DividerGrey),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isMeasuring) "현재 모드: ${purpose.title}" else "측정 목적 선택",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = purpose.description,
                color = Subdued,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            if (!isMeasuring) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PurposeButton(
                        purpose = MeasurementPurpose.CALIBRATION,
                        selected = purpose == MeasurementPurpose.CALIBRATION,
                        onClick = onPurposeSelected,
                        modifier = Modifier.weight(1f),
                    )
                    PurposeButton(
                        purpose = MeasurementPurpose.VALIDATION,
                        selected = purpose == MeasurementPurpose.VALIDATION,
                        onClick = onPurposeSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
                PurposeButton(
                    purpose = MeasurementPurpose.REFERENCE,
                    selected = purpose == MeasurementPurpose.REFERENCE,
                    onClick = onPurposeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = purposeGuide(purpose),
                    color = Subdued,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun PurposeButton(
    purpose: MeasurementPurpose,
    selected: Boolean,
    onClick: (MeasurementPurpose) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
    } else {
        ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink)
    }
    val border = if (selected) null else BorderStroke(1.dp, DividerGrey)
    OutlinedButton(
        onClick = { onClick(purpose) },
        colors = colors,
        border = border,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(purpose.shortTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = purpose.wireValue,
                fontSize = 10.sp,
                color = if (selected) Color.White.copy(alpha = 0.8f) else Subdued,
            )
        }
    }
}

private fun purposeGuide(purpose: MeasurementPurpose): String = when (purpose) {
    MeasurementPurpose.CALIBRATION ->
        "보정용 측정은 AP 근처, 벽 뒤, 구석 등 신호 차이가 나는 위치를 포함하면 보정 품질이 좋아집니다."
    MeasurementPurpose.VALIDATION ->
        "검증용 측정은 보정용 측정과 다른 위치에서 수집하세요. 이 데이터는 보정 계산에 사용하지 않습니다."
    MeasurementPurpose.REFERENCE ->
        "참조맵용 측정은 공간 전체의 RSSI 분포를 보기 위한 데이터입니다. 가능하면 골고루 이동하며 수집하세요."
    MeasurementPurpose.UNKNOWN ->
        "목적이 없으면 백엔드가 기존 데이터 호환 정책에 따라 처리합니다."
}

@Composable
private fun ArStatusOverlay(state: MeasureUiState) {
    val tracking = state.arTrackingState ?: "INITIALIZING"
    val pos = state.currentFloorPosition
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "AR: $tracking",
            color = if (tracking == "TRACKING") Highlight else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (pos != null) {
            Text(
                text = "floor=(${"%.2f".format(pos.x)}, ${"%.2f".format(pos.y)}, ${"%.2f".format(pos.z)})",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun CalibrationInputCard(
    input: CalibrationInputState,
    onFieldChange: (startFloorX: String?, startFloorY: String?, headingDeg: String?) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerGrey),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "도면 기준 시작 위치 / 방향",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "좌표계: origin=좌상단, +x=오른쪽, +y=아래 (단위 m).\n" +
                    "heading: 휴대폰 정면이 도면 +x로부터 회전한 각도, 시계방향(+x→+y) 양수.\n" +
                    "예) 0°=오른쪽, 90°=아래, 180°=왼쪽, 270°=위",
                color = Subdued,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input.startFloorX,
                    onValueChange = { onFieldChange(it, null, null) },
                    label = { Text("startFloorX (m)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = input.startFloorY,
                    onValueChange = { onFieldChange(null, it, null) },
                    label = { Text("startFloorY (m)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = input.initialHeadingDeg,
                onValueChange = { onFieldChange(null, null, it) },
                label = { Text("initialHeadingDeg (°, +x=0, CW+)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FloorPositionCard(state: MeasureUiState) {
    val cur = state.currentFloorPosition
    val last = state.lastUploadedFloorPosition
    val bounds = state.floorBounds
    val border = if (state.isOutOfBounds) BorderStroke(2.dp, Rose) else BorderStroke(1.dp, DividerGrey)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Floor 위치", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "현재: " + (cur?.let { "(${"%.2f".format(it.x)}, ${"%.2f".format(it.y)}, ${"%.2f".format(it.z)})" } ?: "—"),
                color = if (state.isOutOfBounds) Rose else Subdued,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "마지막 업로드: " + (last?.let { "(${"%.2f".format(it.x)}, ${"%.2f".format(it.y)}, ${"%.2f".format(it.z)})" } ?: "—"),
                color = Subdued,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            if (bounds != null) {
                Text(
                    text = "bounds: x ${"%.1f".format(bounds.minX)}~${"%.1f".format(bounds.maxX)}, " +
                        "y ${"%.1f".format(bounds.minY)}~${"%.1f".format(bounds.maxY)}",
                    color = Subdued,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (state.isOutOfBounds) {
                Text(
                    text = "⚠ 현재 좌표가 도면 bounds 밖 (누적 ${state.outOfBoundsCount}점)",
                    color = Rose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ApiUploadStatusCard(state: MeasureUiState) {
    val sessionLabel = state.apiSessionId?.let { "API 세션 $it" } ?: "API 세션 없음 (Dev API에서 생성)"
    val countsLabel = "전송 ${state.serverPointsTotal} · 대기 ${state.pendingBufferSize}"
    val statusLabel = when {
        state.sessionCompleted -> "완료됨"
        state.isCompleting -> "완료 처리 중…"
        state.isUploadingPoints -> "업로드 중…"
        state.apiSessionId == null -> "—"
        else -> "대기 중"
    }
    val detailLabel = state.completionSummary ?: state.lastUploadInfo

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerGrey),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = sessionLabel, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "측정 목적: ${state.measurementPurpose.title}",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = countsLabel, color = Subdued, fontSize = 12.sp)
                Text(text = statusLabel, color = Subdued, fontSize = 12.sp)
            }
            Text(
                text = "수집 포인트: ${state.sampleCount}개 · RSSI 유효 포인트: ${state.serverPointsTotal}개",
                color = Subdued,
                fontSize = 11.sp,
            )
            detailLabel?.let {
                Text(text = it, color = Subdued, fontSize = 11.sp)
            }
            if (state.sessionCompleted) {
                Text(
                    text = completionPurposeNote(state.measurementPurpose),
                    color = Subdued,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

private fun completionPurposeNote(purpose: MeasurementPurpose): String = when (purpose) {
    MeasurementPurpose.CALIBRATION ->
        "보정용 측정은 시뮬레이션 맵을 실제 공간에 맞게 조정하는 데 사용됩니다."
    MeasurementPurpose.VALIDATION ->
        "검증용 측정은 보정 계산에 사용하지 않고, 보정 전/후 예측 오차를 비교하는 데 사용됩니다."
    MeasurementPurpose.REFERENCE ->
        "참조맵용 측정은 실측 기반 RSSI 참조맵을 만드는 데 사용됩니다."
    MeasurementPurpose.UNKNOWN ->
        "목적 미지정 측정은 백엔드의 기존 데이터 호환 정책에 따라 처리됩니다."
}

@Composable
private fun WifiStatusCard(state: MeasureUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = if (state.isMeasuring) Accent else Color(0xFFCBD5E1),
                    modifier = Modifier.size(56.dp),
                )
                val sample = state.lastSample
                if (sample != null) {
                    Text(
                        text = sample.ssid,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Ink,
                    )
                    Text(
                        text = sample.bssid,
                        color = Subdued,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "${sample.frequencyMhz} MHz · 누적 ${state.sampleCount}샘플",
                        color = Subdued,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = if (state.isMeasuring) "Wi-Fi 정보를 수신 중..." else "측정 대기 중",
                        color = Subdued,
                        fontSize = 14.sp,
                    )
                    state.sessionId?.let {
                        Text(text = it, color = Subdued, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(state: MeasureUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MetricCard(
            label = "현재 신호",
            value = state.lastSample?.rssi?.toString() ?: "-",
            unit = "dBm",
            highlighted = state.lastSample != null,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "응답 지연",
            value = "-",
            unit = "ms",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "데이터 속도",
            value = "-",
            unit = "Mbps",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val border = if (highlighted) BorderStroke(2.dp, Highlight)
                 else BorderStroke(1.dp, DividerGrey)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = Subdued, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Text(
                    text = " $unit",
                    fontSize = 11.sp,
                    color = Subdued,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    isMeasuring: Boolean,
    purpose: MeasurementPurpose,
    canUpload: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUpload: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isMeasuring) {
            StopButton(onClick = onStop, modifier = Modifier.weight(1f))
        } else {
            StartButton(purpose = purpose, onClick = onStart, modifier = Modifier.weight(1f))
        }
        UploadButton(enabled = canUpload && !isMeasuring, onClick = onUpload)
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = RoseBg,
            contentColor = Rose,
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.height(56.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(Rose, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text("측정 종료", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun StartButton(
    purpose: MeasurementPurpose,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.height(56.dp),
    ) {
        Text("${purpose.shortTitle} 측정 시작", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun UploadButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerGrey),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFF3F4F6),
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FileUpload,
            contentDescription = "업로드",
            tint = if (enabled) Subdued else Color(0xFFCBD5E1),
        )
    }
}

@Composable
private fun MarkIssueLink(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = Rose,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "이 위치를 문제 지점으로 표시",
            color = Rose,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

/** 측정 시작 / 측정 종료 위의 헤더: QR 스캔 버튼 + 받아온 측정 링크 요약. */
@Composable
private fun ContextHeader(
    state: MeasureUiState,
    onScannedToken: (String) -> Unit,
    onScanError: (String) -> Unit,
    onScanInstallProgress: (String) -> Unit,
) {
    val context = LocalContext.current
    val ctx = state.measureContext

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerGrey),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ctx == null) "측정 링크 미연결" else "측정 링크 연결됨",
                        color = if (ctx == null) Subdued else Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    if (ctx != null) {
                        Text(
                            text = "token=${ctx.token}",
                            color = Subdued,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            text = "오른쪽 QR 버튼을 눌러 측정 링크 QR을 스캔하세요.",
                            color = Subdued,
                            fontSize = 11.sp,
                        )
                    }
                }
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity == null) {
                            onScanError("Activity 없음")
                        } else {
                            QrScanner.start(
                                activity = activity,
                                onResult = onScannedToken,
                                onError = onScanError,
                                onInstallProgress = onScanInstallProgress,
                            )
                        }
                    },
                    enabled = !state.isMeasuring,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (state.isFetchingContext) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "QR 스캔",
                            tint = Color.White,
                        )
                    }
                }
            }

            if (ctx != null) {
                Text(
                    text = "project=${ctx.projectId.take(8)}… · floor=${ctx.floorId.take(8)}…" +
                        (ctx.sceneVersionId?.let { " · scene=${it.take(8)}…" } ?: "") +
                        (ctx.assetId?.let { " · asset=${it.take(8)}…" } ?: " · asset=∅"),
                    color = Subdued,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "recommended purpose: ${ctx.recommendedMeasurementPurpose}",
                    color = Subdued,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (state.isCreatingSession) {
                Text(
                    text = "측정 세션 생성 중…",
                    color = Accent,
                    fontSize = 11.sp,
                )
            } else if (state.apiSessionId != null) {
                Text(
                    text = "API 세션: ${state.apiSessionId}" +
                        if (state.sessionCompleted) " (완료됨)" else "",
                    color = Subdued,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RoseBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = Rose,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = Rose,
                )
            }
        }
    }
}
