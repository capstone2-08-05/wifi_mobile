package com.capstone.mobilemeasure.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UploadMeasurementPointsRequest(
    @SerializedName("batch_id")
    val batchId: String? = null,

    @SerializedName("points")
    val points: List<MeasurementPointDto>,
)

data class MeasurementPointDto(
    @SerializedName("client_point_id")
    val clientPointId: String? = null,

    @SerializedName("floor_position")
    val floorPosition: FloorPositionDto,

    @SerializedName("rssi_dbm")
    val rssiDbm: Double? = null,

    @SerializedName("measurement_purpose")
    val measurementPurpose: String? = null,

    @SerializedName("ap_bssid")
    val apBssid: String? = null,

    @SerializedName("ap_ssid")
    val apSsid: String? = null,

    @SerializedName("channel")
    val channel: Int? = null,

    @SerializedName("frequency_mhz")
    val frequencyMhz: Int? = null,

    @SerializedName("timestamp_at_point")
    val timestampAtPoint: String? = null,

    @SerializedName("ar_tracking_state")
    val arTrackingState: String? = null,

    @SerializedName("ar_confidence")
    val arConfidence: Double? = null,

    @SerializedName("step_index")
    val stepIndex: Int? = null,

    @SerializedName("metadata_json")
    val metadataJson: Map<String, Any?> = emptyMap(),
)

data class FloorPositionDto(
    @SerializedName("x")
    val x: Double,

    @SerializedName("y")
    val y: Double,

    @SerializedName("z")
    val z: Double = 0.0,
)

data class UploadMeasurementPointsResponseDto(
    @SerializedName("inserted")
    val inserted: Int,

    @SerializedName("duplicated")
    val duplicated: Int,

    @SerializedName("session_status")
    val sessionStatus: String,
)
