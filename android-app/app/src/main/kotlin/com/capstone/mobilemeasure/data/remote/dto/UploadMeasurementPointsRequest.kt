package com.capstone.mobilemeasure.data.remote.dto

data class UploadMeasurementPointsRequest(
    val batchId: String,
    val points: List<MeasurementPointDto>
)

data class MeasurementPointDto(
    val clientPointId: String,
    val floorPosition: FloorPositionDto,
    val rssiDbm: Double,
    val apBssid: String?,
    val apSsid: String?,
    val frequencyMhz: Int?,
    val timestampAtPoint: String,
    val arTrackingState: String?,
    val stepIndex: Int
)

data class FloorPositionDto(
    val x: Double,
    val y: Double,
    val z: Double?
)

data class UploadMeasurementPointsResponseDto(
    val inserted: Int,
    val sessionStatus: String
)