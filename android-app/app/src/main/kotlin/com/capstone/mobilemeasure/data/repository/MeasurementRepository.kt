package com.capstone.mobilemeasure.data.repository

import com.capstone.mobilemeasure.data.remote.api.MeasurementApi
import com.capstone.mobilemeasure.data.remote.dto.CompleteMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.CreateMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.MeasureContextDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementCompleteResponseDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementSessionResponseDto
import com.capstone.mobilemeasure.data.remote.dto.UploadMeasurementPointsRequest

class MeasurementRepository(
    private val api: MeasurementApi
) {
    suspend fun getContext(token: String): Result<MeasureContextDto> = runCatching {
        api.getMeasurementContext(token = token)
    }

    suspend fun createSession(
        request: CreateMeasurementSessionRequest
    ): Result<MeasurementSessionResponseDto> = runCatching {
        api.createMeasurementSession(request = request)
    }

    suspend fun uploadPoints(
        sessionId: String,
        request: UploadMeasurementPointsRequest
    ): Result<Int> = runCatching {
        api.uploadMeasurementPoints(sessionId = sessionId, request = request).inserted
    }

    suspend fun completeSession(
        sessionId: String,
        request: CompleteMeasurementSessionRequest
    ): Result<MeasurementCompleteResponseDto> = runCatching {
        api.completeMeasurementSession(sessionId = sessionId, request = request)
    }
}
