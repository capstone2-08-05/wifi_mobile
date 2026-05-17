package com.capstone.mobilemeasure.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.mobilemeasure.arcore.findActivity
import com.capstone.mobilemeasure.data.remote.dto.MeasureContextDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementSessionResponseDto
import com.capstone.mobilemeasure.qr.QrScanner

private val ScreenBg = Color(0xFFF6F7FB)
private val Ink = Color(0xFF111827)
private val Subdued = Color(0xFF6B7280)
private val Accent = Color(0xFF3B82F6)
private val ErrorRed = Color(0xFFE53E5C)
private val DividerGrey = Color(0xFFE5E7EB)

@Composable
fun DevMeasureScreen(
    state: DevMeasureUiState,
    onTokenChange: (String) -> Unit,
    onFetchContext: () -> Unit,
    onCreateSession: () -> Unit,
    onClear: () -> Unit,
    onScannedToken: (String) -> Unit,
    onScanError: (String) -> Unit,
    onScanInstallProgress: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Measurement API 연동 점검 (QR 미사용)",
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            text = "Swagger에서 발급한 measurement token을 붙여 넣어 context/session을 호출합니다.",
            color = Subdued,
            fontSize = 12.sp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.token,
                onValueChange = onTokenChange,
                label = { Text("measurement token") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
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
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "QR 스캔",
                    tint = Color.White,
                )
            }
        }
        Text(
            text = "QR 스캔: 카메라가 잠시 떴다가 token이 자동 입력되고 context를 조회합니다.",
            color = Subdued,
            fontSize = 11.sp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onFetchContext,
                enabled = !state.isFetchingContext,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                if (state.isFetchingContext) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(18.dp),
                    )
                } else {
                    Text("Context 조회", color = Color.White, fontSize = 14.sp)
                }
            }
            Button(
                onClick = onCreateSession,
                enabled = !state.isCreatingSession && state.context != null,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                if (state.isCreatingSession) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(18.dp),
                    )
                } else {
                    Text("Session 생성", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        OutlinedButton(
            onClick = onClear,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Text("결과 지우기", color = Subdued, fontSize = 13.sp)
        }

        state.errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = msg,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        state.context?.let { ContextCard(it) }
        state.session?.let { SessionCard(it) }

        if (state.logs.isNotEmpty()) {
            LogsCard(state.logs)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ContextCard(ctx: MeasureContextDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Context", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
            KvRow("token", ctx.token)
            KvRow("project_id", ctx.projectId)
            KvRow("floor_id", ctx.floorId)
            KvRow("scene_version_id", ctx.sceneVersionId ?: "-")
            KvRow("asset_id", ctx.assetId ?: "-")
            KvRow("expires_at", ctx.expiresAt)
            KvRow("floorplan.url", ctx.floorplan.url ?: "-")
            KvRow(
                "bounds",
                "(${ctx.bounds.minX},${ctx.bounds.minY}) → " +
                    "(${ctx.bounds.maxX},${ctx.bounds.maxY})",
            )
            KvRow(
                "coord",
                "${ctx.coordinateSystem.unit}/${ctx.coordinateSystem.origin}",
            )
        }
    }
}

@Composable
private fun SessionCard(sess: MeasurementSessionResponseDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Session", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
            KvRow("id", sess.id)
            KvRow("status", sess.status)
            KvRow("measurement_type", sess.measurementType)
            KvRow("project_id", sess.projectId)
            KvRow("floor_id", sess.floorId)
            KvRow("scene_version_id", sess.sceneVersionId ?: "-")
            KvRow("asset_id", sess.assetId ?: "-")
            KvRow("created_at", sess.createdAt)
        }
    }
}

@Composable
private fun KvRow(key: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = key,
            color = Subdued,
            fontSize = 12.sp,
            modifier = Modifier.width(width = 130.dp),
        )
        Text(
            text = value,
            color = Ink,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LogsCard(logs: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Logs", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            logs.takeLast(30).forEach { line ->
                Text(
                    text = line,
                    color = Ink,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
