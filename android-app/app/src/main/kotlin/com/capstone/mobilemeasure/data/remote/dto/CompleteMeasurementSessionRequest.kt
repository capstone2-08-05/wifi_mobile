package com.capstone.mobilemeasure.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CompleteMeasurementSessionRequest(
    @SerializedName("end_position")
    val endPosition: FloorPositionDto? = null,
)

data class MeasurementCompleteResponseDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("total_points")
    val totalPoints: Int,

    @SerializedName("duration_seconds")
    val durationSeconds: Int,

    @SerializedName("ap_count")
    val apCount: Int,

    @SerializedName("rssi_range")
    val rssiRange: RssiRangeDto,
)

data class RssiRangeDto(
    @SerializedName("min")
    val min: Double? = null,

    @SerializedName("max")
    val max: Double? = null,

    @SerializedName("avg")
    val avg: Double? = null,
)
