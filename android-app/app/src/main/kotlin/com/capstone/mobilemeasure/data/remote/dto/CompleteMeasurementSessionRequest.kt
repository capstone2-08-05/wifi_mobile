package com.capstone.mobilemeasure.data.remote.dto

data class CompleteMeasurementSessionRequest(
    val completedAt: String,
    val totalPoints: Int,
    val notes: String?
)

data class MeasurementCompleteResponseDto(
    val sessionId: String,
    val status: String,
    val totalPoints: Int,
    val completedAt: String
)
