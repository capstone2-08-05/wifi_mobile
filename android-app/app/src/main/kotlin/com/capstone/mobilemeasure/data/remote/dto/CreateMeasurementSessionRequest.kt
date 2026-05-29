package com.capstone.mobilemeasure.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateMeasurementSessionRequest(
    @SerializedName("measurement_link_token")
    val measurementLinkToken: String,

    @SerializedName("measurement_type")
    val measurementType: String = "rssi",

    @SerializedName("measurement_purpose")
    val measurementPurpose: String = "calibration",

    @SerializedName("device_info")
    val deviceInfo: DeviceInfoDto = DeviceInfoDto(),

    @SerializedName("calibration")
    val calibration: CalibrationDto = CalibrationDto(),
)

data class DeviceInfoDto(
    @SerializedName("model")
    val model: String? = null,

    @SerializedName("os")
    val os: String? = null,

    @SerializedName("app_version")
    val appVersion: String? = null,
)

data class CalibrationDto(
    @SerializedName("method")
    val method: String? = null,

    @SerializedName("start_floor_position")
    val startFloorPosition: FloorPositionDto? = null,

    @SerializedName("initial_heading_deg")
    val initialHeadingDeg: Double? = null,
)

data class MeasurementSessionResponseDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("project_id")
    val projectId: String,

    @SerializedName("floor_id")
    val floorId: String,

    @SerializedName("scene_version_id")
    val sceneVersionId: String?,

    @SerializedName("asset_id")
    val assetId: String?,

    @SerializedName("measurement_type")
    val measurementType: String,

    @SerializedName("measurement_purpose")
    val measurementPurpose: String = "unknown",

    @SerializedName("status")
    val status: String,

    @SerializedName("created_at")
    val createdAt: String,
)
