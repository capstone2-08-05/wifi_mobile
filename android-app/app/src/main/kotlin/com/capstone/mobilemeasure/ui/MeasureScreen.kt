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
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.mobilemeasure.MeasureUiState

private val ScreenBg = Color(0xFFF6F7FB)
private val Highlight = Color(0xFF6FE0C2)
private val Rose = Color(0xFFE53E5C)
private val RoseBg = Color(0xFFFFE4E6)
private val Subdued = Color(0xFF6B7280)
private val DividerGrey = Color(0xFFE5E7EB)
private val Ink = Color(0xFF111827)

@Composable
fun MeasureScreen(
    state: MeasureUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMarkIssue: () -> Unit,
    onUpload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (state.isMeasuring) {
                "매장을 천천히 걸어다니며 구석구석을\n측정하고 있습니다."
            } else {
                "측정 시작 버튼을 눌러\nRSSI 데이터 수집을 시작하세요."
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Subdued,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        WifiStatusCard(state)

        MetricsRow(state)

        ApiUploadStatusCard(state)

        ActionRow(
            isMeasuring = state.isMeasuring,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = countsLabel, color = Subdued, fontSize = 12.sp)
                Text(text = statusLabel, color = Subdued, fontSize = 12.sp)
            }
            detailLabel?.let {
                Text(text = it, color = Subdued, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun WifiStatusCard(state: MeasureUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
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
                    tint = if (state.isMeasuring) Color(0xFF3B82F6) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(64.dp),
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
            StartButton(onClick = onStart, modifier = Modifier.weight(1f))
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
private fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Text("측정 시작", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
