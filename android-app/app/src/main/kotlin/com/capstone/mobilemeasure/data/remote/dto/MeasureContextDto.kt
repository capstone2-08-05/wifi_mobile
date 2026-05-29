package com.capstone.mobilemeasure.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeasureContextDto(
    @SerializedName("token")
    val token: String,

    @SerializedName("project_id")
    val projectId: String,

    @SerializedName("floor_id")
    val floorId: String,

    @SerializedName("scene_version_id")
    val sceneVersionId: String?,

    @SerializedName("asset_id")
    val assetId: String?,

    @SerializedName("expires_at")
    val expiresAt: String,

    @SerializedName("floorplan")
    val floorplan: FloorplanInfoDto = FloorplanInfoDto(),

    @SerializedName("coordinate_system")
    val coordinateSystem: CoordinateSystemDto = CoordinateSystemDto(),

    @SerializedName("bounds")
    val bounds: FloorBoundsDto = FloorBoundsDto(),

    @SerializedName("anchor_points")
    val anchorPoints: List<Map<String, Any?>> = emptyList(),

    @SerializedName("existing_ap_layouts")
    val existingApLayouts: List<Map<String, Any?>> = emptyList(),

    @SerializedName("recommended_measurement_purpose")
    val recommendedMeasurementPurpose: String = "calibration",
)

data class FloorplanInfoDto(
    @SerializedName("url")
    val url: String? = null,

    @SerializedName("width_px")
    val widthPx: Int? = null,

    @SerializedName("height_px")
    val heightPx: Int? = null,

    @SerializedName("scale_m_per_px")
    val scaleMPerPx: Double? = null,
)

data class CoordinateSystemDto(
    @SerializedName("unit")
    val unit: String = "meter",

    @SerializedName("origin")
    val origin: String = "top_left",

    @SerializedName("x_axis")
    val xAxis: String = "right",

    @SerializedName("y_axis")
    val yAxis: String = "down",

    @SerializedName("z_axis")
    val zAxis: String = "up",
)

data class FloorBoundsDto(
    @SerializedName("min_x")
    val minX: Double = 0.0,

    @SerializedName("min_y")
    val minY: Double = 0.0,

    @SerializedName("max_x")
    val maxX: Double = 0.0,

    @SerializedName("max_y")
    val maxY: Double = 0.0,
)
