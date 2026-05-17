package com.capstone.mobilemeasure.arcore

import com.capstone.mobilemeasure.data.remote.dto.FloorBoundsDto
import com.capstone.mobilemeasure.data.remote.dto.FloorPositionDto
import kotlin.math.cos
import kotlin.math.sin

/**
 * ARCore 카메라 pose → 도면(floor) 좌표 변환.
 *
 * ## 좌표계 규약 (백엔드 CoordinateSystemDTO와 합치)
 *
 * Floor 좌표계:
 *   - origin = top_left
 *   - +x: 도면 오른쪽
 *   - +y: 도면 아래쪽   ← y축이 뒤집힌 화면 좌표계 형태
 *   - +z: 위 (천장 방향)
 *   - 단위: meter
 *
 * ARCore 좌표계 (Session 시작 시점 기준):
 *   - +x: 디바이스 오른쪽
 *   - +y: 위 (중력 반대)
 *   - +z: 카메라 뒤쪽  ← 사용자 정면은 -z 방향
 *
 * Heading 규약:
 *   - h = 사용자 휴대폰 정면이 도면 +x축으로부터 회전한 각도 (°).
 *   - **시각적 시계방향(=도면 +x → +y 회전)을 양수**로 본다.
 *     - h=0  → 사용자 정면이 도면 +x(오른쪽)
 *     - h=90 → 사용자 정면이 도면 +y(아래)
 *     - h=180→ 사용자 정면이 도면 -x(왼쪽)
 *     - h=270→ 사용자 정면이 도면 -y(위)
 *
 * ## 변환식 유도
 *
 *   사용자 정면 단위벡터 (floor) = (cos h, sin h)
 *   사용자 오른쪽 단위벡터 (floor) = (-sin h, cos h)   // 정면을 도면상 CW 90° 회전
 *
 *   ARCore 이동량:
 *     deltaArX = arX - initialArX   (사용자 오른쪽 이동)
 *     deltaArZ = arZ - initialArZ   (사용자 뒤쪽 이동, 정면 이동은 -deltaArZ)
 *
 *   floor 이동 = (-deltaArZ) * 정면 + deltaArX * 오른쪽
 *     dx = -deltaArZ * cos h  +  deltaArX * (-sin h)
 *     dy = -deltaArZ * sin h  +  deltaArX *   cos h
 *
 *   floorX = startFloorX + dx
 *   floorY = startFloorY + dy
 *   floorZ = startFloorZ                  // ARCore는 절대 고도가 없어 시작 z를 유지
 *
 * Why: 이 식대로 두면 heading=0에서 사용자가 정면 1m 걸으면 floor +x가 +1m,
 * 오른쪽 1m 걸으면 floor +y(도면 아래)가 +1m 가 되어 백엔드 좌표계와 직관이 일치한다.
 */
object FloorPositionMapper {

    fun map(
        calibration: FloorCalibrationState,
        arTx: Float,
        arTz: Float,
    ): FloorPositionDto {
        val deltaArX = (arTx - calibration.initialArX).toDouble()
        val deltaArZ = (arTz - calibration.initialArZ).toDouble()
        val rad = Math.toRadians(calibration.initialHeadingDeg)
        val cosH = cos(rad)
        val sinH = sin(rad)
        val dx = -deltaArZ * cosH + deltaArX * -sinH
        val dy = -deltaArZ * sinH + deltaArX * cosH
        return FloorPositionDto(
            x = calibration.startFloorX + dx,
            y = calibration.startFloorY + dy,
            z = calibration.startFloorZ,
        )
    }

    fun map(
        calibration: FloorCalibrationState,
        pose: ArPoseSnapshot,
    ): FloorPositionDto = map(calibration, pose.tx, pose.tz)

    /** bounds 안에 있는지 검사. bounds가 0-크기이면 검증 안 함(true 반환). */
    fun isInsideBounds(pos: FloorPositionDto, bounds: FloorBoundsDto): Boolean {
        if (bounds.maxX <= bounds.minX || bounds.maxY <= bounds.minY) return true
        return pos.x in bounds.minX..bounds.maxX && pos.y in bounds.minY..bounds.maxY
    }
}
