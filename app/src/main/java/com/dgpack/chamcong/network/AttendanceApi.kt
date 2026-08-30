package com.dgpack.chamcong.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AttendanceApi {
    /**
     * Mục [5.2]: POST /api/v1/attendance/sync-events, header X-Attendance-Api-Key bắt buộc.
     * Batch tối đa 200 phần tử/lần (mục [5.3]) — caller (SyncRepository) chịu trách nhiệm chia batch.
     */
    @POST("api/v1/attendance/sync-events")
    suspend fun syncEvents(
        @Header("X-Attendance-Api-Key") apiKey: String,
        @Body events: List<SyncEventRequest>
    ): Response<List<SyncEventResult>>
}
