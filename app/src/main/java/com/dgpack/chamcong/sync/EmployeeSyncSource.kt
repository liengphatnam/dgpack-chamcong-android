package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.ErpEmployeeEntity

/**
 * Phần EmployeeRepository mà [EmployeeSyncEngine] cần — tách interface để test engine
 * bằng fake in-memory, không cần Room thật.
 */
interface EmployeeSyncSource {
    /** Thay toàn bộ bảng erp_employee bằng danh sách mới kéo về. */
    suspend fun replaceErpEmployees(employees: List<ErpEmployeeEntity>)
}
