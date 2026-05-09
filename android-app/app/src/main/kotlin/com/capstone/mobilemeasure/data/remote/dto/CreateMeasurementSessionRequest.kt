package com.capstone.mobilemeasure.data.remote.dto

data class CreateMeasurementSessionRequest(
    val linkToken: String,
    val measurementType: String,
    val deviceInfo: DeviceInfoDto,
    val notes: String?
)

data class DeviceInfoDto(
    val osName: String,
    val osVersion: String,
    val deviceModel: String,
    val appVersion: String
)

data class MeasurementSessionResponseDto(
    val sessionId: String,
    val projectId: String,
    val floorId: String,
    val createdAt: String
)
