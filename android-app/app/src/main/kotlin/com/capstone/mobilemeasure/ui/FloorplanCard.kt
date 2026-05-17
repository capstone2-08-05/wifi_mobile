package com.capstone.mobilemeasure.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.capstone.mobilemeasure.data.remote.dto.FloorBoundsDto
import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto
import com.capstone.mobilemeasure.data.remote.dto.FloorplanInfoDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Ink = Color(0xFF111827)
private val Subdued = Color(0xFF6B7280)
private val DividerGrey = Color(0xFFE5E7EB)
private val Accent = Color(0xFF3B82F6)
private val Rose = Color(0xFFE53E5C)

/**
 * 도면 이미지 + 시작/현재 위치 점 + heading 화살표 표시.
 *
 * editable=true 일 때 도면 위 드래그로 시작 위치 + heading을 한 번에 지정할 수 있다:
 *   - 손가락을 내려놓는 지점 = 시작 위치 (floor 좌표)
 *   - 드래그 방향 = heading (사용자 정면이 floor +x로부터 회전한 각도, CW 양수)
 *   - 드래그 거리가 너무 짧으면 heading은 갱신하지 않고 위치만 변경
 *
 * heading 규약은 FloorPositionMapper와 동일해야 한다 (atan2의 결과가 그대로 들어맞도록
 * 화면 좌표 y가 floor +y와 같은 방향이라는 점을 이용).
 */
@Composable
fun FloorplanCard(
    floorplan: FloorplanInfoDto?,
    bounds: FloorBoundsDto?,
    currentPosition: FloorPositionDto?,
    startPosition: FloorPositionDto?,
    headingDeg: Double?,
    isOutOfBounds: Boolean,
    editable: Boolean,
    onRefresh: () -> Unit,
    onCalibrationPicked: (floorX: Double, floorY: Double, headingDeg: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerGrey),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("도면", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRefresh) {
                    Text("도면 다시 받기", color = Accent, fontSize = 12.sp)
                }
            }

            val url = floorplan?.url
            if (url.isNullOrBlank()) {
                val msg = if (floorplan == null) {
                    "Context 미조회 — Dev API에서 QR 스캔 또는 token 조회를 먼저 해주세요"
                } else {
                    "이 측정 링크에 도면 자산이 없습니다 (asset_id=null).\n" +
                        "백엔드에 floorplan_image asset을 업로드한 뒤 측정 링크를 다시 만들어 주세요."
                }
                EmptyState(msg)
            } else {
                FloorplanCanvas(
                    url = url,
                    floorplan = floorplan,
                    bounds = bounds,
                    currentPosition = currentPosition,
                    startPosition = startPosition,
                    headingDeg = headingDeg,
                    isOutOfBounds = isOutOfBounds,
                    editable = editable,
                    onRefresh = onRefresh,
                    onCalibrationPicked = onCalibrationPicked,
                )
                if (editable) {
                    Text(
                        text = "도면을 누른 채로 드래그 → 시작 위치 + 방향 지정. 짧게 탭만 하면 위치만 변경.",
                        color = Subdued,
                        fontSize = 11.sp,
                    )
                }
                FloorplanMeta(floorplan = floorplan, bounds = bounds)
            }
        }
    }
}

@Composable
private fun FloorplanCanvas(
    url: String,
    floorplan: FloorplanInfoDto,
    bounds: FloorBoundsDto?,
    currentPosition: FloorPositionDto?,
    startPosition: FloorPositionDto?,
    headingDeg: Double?,
    isOutOfBounds: Boolean,
    editable: Boolean,
    onRefresh: () -> Unit,
    onCalibrationPicked: (floorX: Double, floorY: Double, headingDeg: Double?) -> Unit,
) {
    val widthPx = floorplan.widthPx ?: 4
    val heightPx = floorplan.heightPx ?: 3
    val aspect = if (heightPx > 0) widthPx.toFloat() / heightPx.toFloat() else (4f / 3f)

    val context = LocalContext.current
    val stableKey = remember(url) { url.substringBefore('?') }
    val request = remember(url, stableKey) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(stableKey)
            .diskCacheKey(stableKey)
            .crossfade(true)
            .build()
    }

    var loadState: AsyncImagePainter.State by remember(stableKey) {
        mutableStateOf(AsyncImagePainter.State.Empty)
    }

    // 드래그 임시 상태 (편집 중에만 의미 있음)
    var dragStartPx by remember { mutableStateOf<Offset?>(null) }
    var dragCurPx by remember { mutableStateOf<Offset?>(null) }

    val rangeX = bounds?.let { it.maxX - it.minX } ?: 0.0
    val rangeY = bounds?.let { it.maxY - it.minY } ?: 0.0
    val hasBounds = bounds != null && rangeX > 0.0 && rangeY > 0.0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.3f, 4f))
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF3F4F6))
            .then(
                if (editable && hasBounds) {
                    Modifier.pointerInput(bounds, rangeX, rangeY) {
                        detectDragGestures(
                            onDragStart = { off ->
                                dragStartPx = off
                                dragCurPx = off
                            },
                            onDrag = { change, _ ->
                                dragCurPx = change.position
                            },
                            onDragEnd = {
                                val start = dragStartPx
                                val end = dragCurPx
                                if (start != null && end != null && size.width > 0 && size.height > 0 && bounds != null) {
                                    val sx = bounds.minX + (start.x / size.width.toDouble()) * rangeX
                                    val sy = bounds.minY + (start.y / size.height.toDouble()) * rangeY
                                    val dx = (end.x - start.x).toDouble()
                                    val dy = (end.y - start.y).toDouble()
                                    val dist = hypot(dx, dy)
                                    // 너무 짧은 드래그는 heading 의미 없음 → 위치만 갱신
                                    val heading = if (dist >= MIN_HEADING_DRAG_PX) {
                                        Math.toDegrees(atan2(dy, dx))
                                    } else {
                                        null
                                    }
                                    onCalibrationPicked(sx, sy, heading)
                                }
                                dragStartPx = null
                                dragCurPx = null
                            },
                            onDragCancel = {
                                dragStartPx = null
                                dragCurPx = null
                            },
                        )
                    }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = "floorplan",
            modifier = Modifier.fillMaxSize(),
            onState = { loadState = it },
        )

        when (val s = loadState) {
            is AsyncImagePainter.State.Loading -> CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
            is AsyncImagePainter.State.Error -> ImageErrorOverlay(
                reason = s.result.throwable.message ?: s.result.throwable.javaClass.simpleName,
                onRefresh = onRefresh,
            )
            else -> Unit
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!hasBounds || bounds == null) return@Canvas

            fun toCanvas(p: FloorPositionDto): Offset {
                val nx = ((p.x - bounds.minX) / rangeX).toFloat().coerceIn(-0.2f, 1.2f)
                val ny = ((p.y - bounds.minY) / rangeY).toFloat().coerceIn(-0.2f, 1.2f)
                return Offset(nx * size.width, ny * size.height)
            }

            // 확정된 시작 위치 + heading 화살표
            startPosition?.let { s ->
                val o = toCanvas(s)
                drawCircle(color = Color(0x80000000), radius = 10f, center = o)
                drawCircle(color = Color.White, radius = 6f, center = o)
                headingDeg?.let { h ->
                    val rad = Math.toRadians(h)
                    val arrowLen = minOf(size.width, size.height) * 0.14f
                    val tip = Offset(
                        o.x + (cos(rad) * arrowLen).toFloat(),
                        o.y + (sin(rad) * arrowLen).toFloat(),
                    )
                    drawArrow(o, tip, Color.White, strokeWidth = 4f)
                    drawArrow(o, tip, Ink, strokeWidth = 2f)
                }
            }

            // 드래그 임시 표시
            val ds = dragStartPx
            val de = dragCurPx
            if (ds != null && de != null) {
                drawCircle(color = Accent.copy(alpha = 0.3f), radius = 16f, center = ds)
                drawCircle(color = Accent, radius = 8f, center = ds)
                if (hypot((de.x - ds.x).toDouble(), (de.y - ds.y).toDouble()) >= MIN_HEADING_DRAG_PX) {
                    drawArrow(ds, de, Accent, strokeWidth = 3f)
                }
            }

            // 현재 위치 (측정 중)
            currentPosition?.let { c ->
                val o = toCanvas(c)
                val ring = if (isOutOfBounds) Rose else Accent
                drawCircle(color = ring.copy(alpha = 0.35f), radius = 22f, center = o)
                drawCircle(color = ring, radius = 12f, center = o)
                drawCircle(color = Color.White, radius = 5f, center = o)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(color = color, start = from, end = to, strokeWidth = strokeWidth)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (len < 1f) return
    val ux = dx / len
    val uy = dy / len
    val headSize = (strokeWidth * 4f).coerceAtLeast(10f)
    // 화살촉: 끝점에서 뒤로 -uxRot 만큼 후퇴한 두 점
    val cos30 = 0.8660254f
    val sin30 = 0.5f
    val backX = -ux * headSize
    val backY = -uy * headSize
    val left = Offset(
        to.x + backX * cos30 - backY * sin30,
        to.y + backY * cos30 + backX * sin30,
    )
    val right = Offset(
        to.x + backX * cos30 + backY * sin30,
        to.y + backY * cos30 - backX * sin30,
    )
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
    drawPath(path = path, color = color)
}

@Composable
private fun ImageErrorOverlay(reason: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "도면을 불러오지 못했습니다",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "presigned URL이 만료되었거나 네트워크가 끊겼을 수 있습니다.",
            color = Color(0xFFE5E7EB),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = reason,
            color = Color(0xFF9CA3AF),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        TextButton(onClick = onRefresh) {
            Text("도면 다시 받기", color = Accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FloorplanMeta(floorplan: FloorplanInfoDto, bounds: FloorBoundsDto?) {
    val parts = buildList {
        floorplan.widthPx?.let { add("${it}px") }
        floorplan.heightPx?.let { add("${it}px") }
        floorplan.scaleMPerPx?.let { add("${"%.4f".format(it)} m/px") }
        bounds?.let { add("bounds ${"%.1f".format(it.maxX - it.minX)}×${"%.1f".format(it.maxY - it.minY)} m") }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            color = Subdued,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF3F4F6)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Subdued,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MIN_HEADING_DRAG_PX = 24.0
