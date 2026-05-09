package com.capstone.mobilemeasure.data.remote.dto

data class MeasureContextDto(
    val linkToken: String,
    val projectId: String,
    val projectName: String,
    val floorId: String,
    val floorName: String,
    val floorIndex: Int,
    val floorImageUrl: String?,
    val expiresAt: String?
)
