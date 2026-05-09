package com.capstone.mobilemeasure.data.remote.api

import com.capstone.mobilemeasure.data.remote.dto.CompleteMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.CreateMeasurementSessionRequest
import com.capstone.mobilemeasure.data.remote.dto.MeasureContextDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementCompleteResponseDto
import com.capstone.mobilemeasure.data.remote.dto.MeasurementSessionResponseDto
import com.capstone.mobilemeasure.data.remote.dto.UploadMeasurementPointsRequest
import com.capstone.mobilemeasure.data.remote.dto.UploadMeasurementPointsResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MeasurementApi {

    @GET("measurement-links/{token}/context")
    suspend fun getMeasurementContext(
        @Path("token") token: String
    ): MeasureContextDto

    @POST("measurement-sessions")
    suspend fun createMeasurementSession(
        @Body request: CreateMeasurementSessionRequest
    ): MeasurementSessionResponseDto

    @POST("measurement-sessions/{sessionId}/points")
    suspend fun uploadMeasurementPoints(
        @Path("sessionId") sessionId: String,
        @Body request: UploadMeasurementPointsRequest
    ): UploadMeasurementPointsResponseDto

    @POST("measurement-sessions/{sessionId}/complete")
    suspend fun completeMeasurementSession(
        @Path("sessionId") sessionId: String,
        @Body request: CompleteMeasurementSessionRequest
    ): MeasurementCompleteResponseDto
}
