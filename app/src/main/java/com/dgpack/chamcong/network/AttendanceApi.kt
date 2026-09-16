package com.dgpack.chamcong.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

    // ===== Đồng bộ nhân viên + embedding khuôn mặt — contract MỚI, xem API_FACE_SYNC.md =====
    // Cùng header X-Attendance-Api-Key, cùng cách xử lý 401/503. Server ERP trả 404 nếu chưa
    // triển khai — app hiển thị rõ thay vì coi là lỗi lạ.

    /** Danh sách dm.Employee kèm cờ đã có embedding trên server hay chưa. */
    @GET("api/v1/attendance/employees")
    suspend fun getEmployees(
        @Header("X-Attendance-Api-Key") apiKey: String
    ): Response<List<ErpEmployeeDto>>

    /** Đẩy embedding enroll ở thiết bị này lên server (upsert theo NV + model). */
    @POST("api/v1/attendance/face-embeddings")
    suspend fun uploadFaceEmbeddings(
        @Header("X-Attendance-Api-Key") apiKey: String,
        @Body embeddings: List<FaceEmbeddingUploadRequest>
    ): Response<List<FaceEmbeddingUploadResult>>

    /** Tải toàn bộ embedding server đang giữ (NV enroll ở thiết bị khác). */
    @GET("api/v1/attendance/face-embeddings")
    suspend fun getFaceEmbeddings(
        @Header("X-Attendance-Api-Key") apiKey: String
    ): Response<List<FaceEmbeddingDownloadDto>>
}
