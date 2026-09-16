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
    val syncedAt: String
)
