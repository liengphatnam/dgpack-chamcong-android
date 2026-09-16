package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.EnrolledEmployeeEntity
import com.dgpack.chamcong.data.db.ErpEmployeeEntity

/**
 * Phần EmployeeRepository mà [EmployeeSyncEngine] cần — tách interface để test engine
 * bằng fake in-memory (EmployeeSyncEngineTest), không cần Room thật.
 */
interface EmployeeSyncSource {
    /** Toàn bộ NV đã enroll trên máy này (kể cả đã tải lên hay chưa). */
    suspend fun getAllEnrolled(): List<EnrolledEmployeeEntity>

    suspend fun getEnrolledByCode(employeeCode: String): EnrolledEmployeeEntity?

    /** Server đã nhận embedding — ghi lại mốc để lần sau không gửi lại. */
    suspend fun markFaceUploaded(employeeCode: String, uploadedAt: String)

    /**
     * Lưu embedding tải từ server về (NV enroll ở thiết bị khác). [fullName] null nếu
     * ERP không có tên (hiếm) — repository tự dùng mã NV làm tên hiển thị.
     * Giữ nguyên photoSample cũ nếu đã có.
     */
    suspend fun saveEmbeddingFromServer(
        employeeCode: String,
        fullName: String?,
        embedding: FloatArray,
        updatedAt: String
    )

    /** Thay toàn bộ bảng erp_employee bằng danh sách mới kéo về. */
    suspend fun replaceErpEmployees(employees: List<ErpEmployeeEntity>)
}
