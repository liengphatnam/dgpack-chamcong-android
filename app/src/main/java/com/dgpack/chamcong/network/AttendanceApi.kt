package com.dgpack.chamcong.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

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

    // ===== Đồng bộ nhân viên / thẻ từ / trúng thưởng — xem API_FACE_SYNC.md =====
    // Cùng header X-Attendance-Api-Key, cùng cách xử lý 401/503. Server ERP trả 404 nếu chưa
    // triển khai — app hiển thị rõ thay vì coi là lỗi lạ.

    /** Danh sách dm.Employee (tên, thẻ từ, ngày sinh, trễ/sớm, khen thưởng). */
    @GET("api/v1/attendance/employees")
    suspend fun getEmployees(
        @Header("X-Attendance-Api-Key") apiKey: String
    ): Response<List<ErpEmployeeDto>>

    /**
     * Đẩy sổ người trúng thưởng lon nước ngọt lên ERP để nhân sự phát thưởng
     * (API_FACE_SYNC.md mục 4). Server chưa có endpoint (404) -> app bỏ qua, giữ Pending.
     */
    @POST("api/v1/attendance/lucky-draws")
    suspend fun uploadLuckyDraws(
        @Header("X-Attendance-Api-Key") apiKey: String,
        @Body draws: List<LuckyDrawUploadRequest>
    ): Response<List<LuckyDrawUploadResult>>

    // ===== Thẻ từ — API_FACE_SYNC.md mục 5–7. 404 = ERP chưa triển khai, app bỏ qua, giữ Pending. =====

    /** Thẻ gán trên thiết bị (upsert theo cardId). */
    @POST("api/v1/attendance/cards")
    suspend fun uploadCardAssignments(
        @Header("X-Attendance-Api-Key") apiKey: String,
        @Body cards: List<CardAssignmentUploadRequest>
    ): Response<List<CardAssignmentUploadResult>>

    /** Nhật ký quên thẻ kèm ảnh bằng chứng. */
    @POST("api/v1/attendance/forgot-card")
    suspend fun uploadForgotCards(
        @Header("X-Attendance-Api-Key") apiKey: String,
        @Body logs: List<ForgotCardUploadRequest>
    ): Response<List<ForgotCardUploadResult>>

    /** Chi tiết công tháng của toàn bộ NV (tới hết hôm qua) — cache để hiện sau khi quét thẻ. */
    @GET("api/v1/attendance/month-summary")
    suspend fun getMonthSummary(
        @Header("X-Attendance-Api-Key") apiKey: String,
        @Query("month") month: String
    ): Response<List<MonthSummaryDto>>
}
