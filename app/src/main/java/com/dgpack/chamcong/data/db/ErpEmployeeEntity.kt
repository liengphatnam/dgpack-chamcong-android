package com.dgpack.chamcong.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bản sao (read-only) danh sách dm.Employee kéo từ ERP qua GET api/v1/attendance/employees
 * (xem API_FACE_SYNC.md). Chỉ dùng để admin thấy NV nào CHƯA có khuôn mặt và bấm vào
 * enroll nhanh — KHÔNG phải nguồn sự thật, ERP mới là nguồn; mỗi lần đồng bộ sẽ thay
 * toàn bộ bảng.
 */
@Entity(tableName = "erp_employee")
data class ErpEmployeeEntity(
    @PrimaryKey
    val employeeCode: String,
    val fullName: String,
    val isActive: Boolean,
    /** Server đã có embedding của NV này (từ thiết bị này hoặc thiết bị khác). */
    val hasFaceOnServer: Boolean,
    val faceUpdatedAt: String?,
    /** ISO-8601 UTC thời điểm kéo danh sách này về. */
    val syncedAt: String,
    // ---- Dữ liệu cho chương trình trúng thưởng lon nước ngọt (LuckyDrawEngine) ----
    /** Ngày sinh "yyyy-MM-dd" (null nếu ERP không có) — đúng ngày sinh nhật = chắc chắn trúng 3 lon. */
    val birthDate: String? = null,
    /** Số lần đi trễ/về sớm trong 30 ngày gần nhất (ERP tính) — 0 lần = trọng số x2. */
    val lateEarlyCount30d: Int = 0,
    /** Số lần được khen thưởng / phối hợp nội quy ghi trong Log ERP (trong đợt) — mỗi lần +50% trọng số. */
    val commendationCount: Int = 0
)
