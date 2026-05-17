package com.capstone.mobilemeasure.arcore

/**
 * ARCore 카메라 pose 스냅샷. 측정 point 생성 시점에 metadata로 함께 저장한다.
 *
 * tx/ty/tz는 ARCore 세션 시작 시점 기준 상대 translation (m).
 * trackingState는 com.google.ar.core.TrackingState.name() 값.
 */
data class ArPoseSnapshot(
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
    val trackingState: String,
    val timestampNs: Long,
) {
    val isTracking: Boolean get() = trackingState == "TRACKING"
}
