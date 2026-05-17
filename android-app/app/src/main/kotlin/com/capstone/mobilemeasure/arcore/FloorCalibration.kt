package com.capstone.mobilemeasure.arcore

import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto

/**
 * 사용자가 측정 시작 전에 입력한 시작 위치와 방향 + 그 시점의 ARCore 초기 pose.
 *
 * 좌표계 규약은 [FloorPositionMapper] 문서를 따른다:
 *   - floor: origin=top_left, +x=오른쪽, +y=아래(화면 좌표계), 단위 m
 *   - heading: +x로부터 시각적 시계방향(CW) 양수 (h=0 → +x, h=90 → +y)
 */
data class FloorCalibrationState(
    val startFloorX: Double,
    val startFloorY: Double,
    val startFloorZ: Double = DEFAULT_FLOOR_Z,
    val initialHeadingDeg: Double,
    /** AR 세션 시작 시점의 카메라 translation (m). */
    val initialArX: Double,
    val initialArY: Double,
    val initialArZ: Double,
) {
    fun toStartFloorPositionDto(): FloorPositionDto =
        FloorPositionDto(x = startFloorX, y = startFloorY, z = startFloorZ)

    companion object {
        const val DEFAULT_FLOOR_Z: Double = 1.2
    }
}
