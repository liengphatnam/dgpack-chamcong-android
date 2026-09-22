package com.dgpack.chamcong.network

import kotlinx.serialization.Serializable

/*
 * Contract đồng bộ NHÂN VIÊN / THẺ TỪ / TRÚNG THƯỞNG với ERP — xem API_FACE_SYNC.md. Dùng chung
 * header X-Attendance-Api-Key và cách xử lý 401/503 như sync-events. (Tên file giữ nguyên vì
 * lịch sử; app không còn đồng bộ embedding khuôn mặt.)
 */

/** GET api/v1/attendance/employees — 1 phần tử = 1 dòng dm.Employee. */
@Serializable
data class ErpEmployeeDto(
    val employeeCode: String,
    val fullName: String,
    val isActive: Boolean = true,
    /** Còn giữ để tương thích server cũ; app không dùng nữa. */
    val hasFaceEmbedding: Boolean = false,
    val faceUpdatedAt: String? = null,
    // ---- Chương trình trúng thưởng lon nước ngọt (API_FACE_SYNC.md mục 4) — đều tuỳ chọn ----
    /** Ngày sinh "yyyy-MM-dd" (chấp nhận cả "yyyy-MM-ddT00:00:00" kiểu .NET DateTime). */
    val birthDate: String? = null,
    /** Số lần đi trễ/về sớm trong 30 ngày gần nhất (ERP tính từ hr.AttendanceEvent so với ca). */
    val lateEarlyCount30d: Int = 0,
    /** Số lần được khen thưởng / phối hợp nội quy ghi nhận trong Log ERP trong đợt. */
    val commendationCount: Int = 0,
    /** Mã thẻ từ (UID NFC hex hoặc số in trên thẻ) đã gán cho NV bên ERP, null nếu chưa. */
    val cardId: String? = null
)

// ===== Thẻ từ (API_FACE_SYNC.md mục 5–7) =====

/** POST api/v1/attendance/cards — thẻ gán trên thiết bị, upsert theo cardId. */
@Serializable
data class CardAssignmentUploadRequest(
    val cardId: String,
    val employeeCode: String,
    /** UTC lúc gán trên máy. */
    val assignedAt: String,
    val deviceCode: String?
)

@Serializable
data class CardAssignmentUploadResult(
    val cardId: String,
    val status: String
) {
    companion object {
        const val STATUS_SAVED = "Saved"
        const val STATUS_UNKNOWN_EMPLOYEE = "UnknownEmployee"
    }
}

/** POST api/v1/attendance/forgot-card — 1 lần quên thẻ kèm ảnh bằng chứng nhỏ (JPEG base64). */
@Serializable
data class ForgotCardUploadRequest(
    val employeeCode: String,
    /** UTC "yyyy-MM-ddTHH:mm:ss". */
    val eventTime: String,
    val deviceCode: String?,
    val photoBase64: String?
)

@Serializable
data class ForgotCardUploadResult(
    val employeeCode: String,
    val eventTime: String,
    val status: String
) {
    companion object {
        const val STATUS_SAVED = "Saved"
        const val STATUS_DUPLICATE = "Duplicate"
        const val STATUS_UNKNOWN_EMPLOYEE = "UnknownEmployee"
    }
}

/** GET api/v1/attendance/month-summary?month=yyyy-MM — chi tiết công tháng của từng NV (tới hết hôm qua). */
@Serializable
data class MonthSummaryDto(
    val employeeCode: String,
    val workDays: Double = 0.0,
    val otRegularHours: Double = 0.0,
    val otSundayHours: Double = 0.0,
    val otHolidayHours: Double = 0.0,
    val leaveDays: Double = 0.0,
    val disciplinaryCount: Int = 0,
    val commendationCount: Int = 0,
    val forgotCardCount: Int = 0,
    val penaltyAmount: Long = 0
)

// ===== Trúng thưởng lon nước ngọt (API_FACE_SYNC.md mục 4) =====

/** POST api/v1/attendance/lucky-draws — 1 phần tử = 1 lần trúng thưởng trên thiết bị. */
@Serializable
data class LuckyDrawUploadRequest(
    val employeeCode: String,
    /** Ngày trúng theo giờ VN, "yyyy-MM-dd". */
    val drawDate: String,
    /** UTC "yyyy-MM-ddTHH:mm:ss". */
    val wonAt: String,
    val cans: Int,
    /** "Random" | "Birthday" — xem LuckyDrawReason. */
    val reason: String,
    /** Xác suất 0..1 tại lúc quay, để HR đối chiếu. */
    val chance: Double,
    val deviceCode: String?
)

/** Response của POST lucky-draws, cùng thứ tự với request. */
@Serializable
data class LuckyDrawUploadResult(
    val employeeCode: String,
    val wonAt: String,
    val status: String
) {
    companion object {
        const val STATUS_SAVED = "Saved"
        const val STATUS_DUPLICATE = "Duplicate"
        const val STATUS_UNKNOWN_EMPLOYEE = "UnknownEmployee"
    }
}
