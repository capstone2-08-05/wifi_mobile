package com.capstone.mobilemeasure.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

private val Ink = Color(0xFF111827)
private val Subdued = Color(0xFF6B7280)
private val DividerGrey = Color(0xFFE5E7EB)
private val Accent = Color(0xFF3B82F6)
private val Rose = Color(0xFFE53E5C)

/**
 * 백엔드에서 받은 floorplan presigned URL을 표시하고 그 위에 현재/시작 위치를 점으로 찍는다.
 *
 * Presigned URL은 매번 X-Amz-* 쿼리가 바뀌므로 Coil 디스크/메모리 캐시 키는
 * 쿼리를 제외한 path 부분만 사용한다 (동일 asset이면 키가 안정).
 */
@Composable
fun FloorplanCard(
    floorplan: FloorplanInfoDto?,
    bounds: FloorBoundsDto?,
    currentPosition: FloorPositionDto?,
    startPosition: FloorPositionDto?,
    isOutOfBounds: Boolean,
    onRefresh: () -> Unit,
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
                    isOutOfBounds = isOutOfBounds,
                    onRefresh = onRefresh,
                )
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
    isOutOfBounds: Boolean,
    onRefresh: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.3f, 4f))
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF3F4F6)),
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
            val rangeX = bounds?.let { it.maxX - it.minX } ?: 0.0
            val rangeY = bounds?.let { it.maxY - it.minY } ?: 0.0
            if (bounds == null || rangeX <= 0.0 || rangeY <= 0.0) return@Canvas

            fun toCanvas(p: FloorPositionDto): Offset {
                val nx = ((p.x - bounds.minX) / rangeX).toFloat().coerceIn(-0.2f, 1.2f)
                val ny = ((p.y - bounds.minY) / rangeY).toFloat().coerceIn(-0.2f, 1.2f)
                return Offset(nx * size.width, ny * size.height)
            }

            startPosition?.let { s ->
                val o = toCanvas(s)
                drawCircle(color = Color(0x80000000), radius = 10f, center = o)
                drawCircle(color = Color.White, radius = 6f, center = o)
            }
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
